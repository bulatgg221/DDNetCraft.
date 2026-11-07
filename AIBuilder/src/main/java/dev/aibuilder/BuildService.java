package dev.aibuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Places/undos blocks in batches only on the integrated server tick thread. */
public final class BuildService {
    public static final BuildService INSTANCE = new BuildService();
    private BuildService() {}
    public record Change(BlockPos pos, BlockState before, BlockState placed) {}

    private static final class Job {
        final ServerWorld world;
        final UUID owner;
        final String name;
        final ArrayDeque<PlanCompiler.Placement> queue;
        final List<Change> changes = new ArrayList<>();
        final Deque<Change> undo;
        final Deque<Change> redo;
        final List<Change> successfullyUndone = new ArrayList<>();
        final int total;
        int done, skipped;

        Job(ServerWorld world, UUID owner, PlanCompiler.CompiledPlan plan) {
            this.world = world;
            this.owner = owner;
            this.name = plan.name();
            this.queue = new ArrayDeque<>(plan.blocks());
            this.total = queue.size();
            this.undo = null;
            this.redo = null;
        }
        Job(ServerWorld world, UUID owner, Deque<Change> undo) {
            this.world = world;
            this.owner = owner;
            this.name = "Отмена последней постройки";
            this.queue = null;
            this.undo = new ArrayDeque<>(undo);
            this.redo = null;
            this.total = undo.size();
        }
        Job(ServerWorld world, UUID owner, List<Change> redo) {
            this.world=world;
            this.owner=owner;
            this.name="Повтор последней постройки";
            this.queue=null;
            this.undo=null;
            this.redo=new ArrayDeque<>(redo);
            this.total=redo.size();
        }
        boolean isUndo() { return undo != null; }
        boolean isRedo() { return redo != null; }
    }

    private record Preview(ServerWorld world,UUID owner,
                           PlanCompiler.CompiledPlan plan,
                           BuildQualityAnalyzer.Assessment quality) {}
    private Preview pendingPreview;
    private Job active;
    private Deque<Change> lastUndo = new ArrayDeque<>();
    private List<Change> lastRedo = List.of();
    private ServerWorld lastWorld;
    private UUID lastOwner;
    private int token;
    private boolean planning;
    private long ticks;

    public BuildService initialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        return this;
    }

    public boolean isBusy() {
        return planning || active != null || pendingPreview != null;
    }
    public int beginPlanning() {
        if (isBusy()) return -1;
        planning = true;
        return ++token;
    }
    public boolean isPending(int ticket) { return planning && token == ticket; }
    public void endPlanning(int ticket) {
        if (isPending(ticket)) planning = false;
    }

    /** Display a read-only inspection before ANY block is changed. */
    public void offerPreview(ServerPlayerEntity player,PlanCompiler.CompiledPlan plan,
                             BuildQualityAnalyzer.Assessment quality) {
        if(planning || active!=null || pendingPreview!=null)
            throw new IllegalStateException("Другая генерация ещё выполняется.");
        pendingPreview=new Preview(player.getServerWorld(),player.getUuid(),plan,quality);
        tell(player,"ПРЕДПРОСМОТР «"+plan.name()+"»: "+quality.summary()+".");
        if(!quality.dominantMaterials().isEmpty())
            tell(player,"Основные материалы: "+String.join(", ",quality.dominantMaterials())+".");
        for(int i=0;i<Math.min(5,quality.warnings().size());i++)
            tell(player,"⚠ "+quality.warnings().get(i));
        if(quality.warnings().size()>5)
            tell(player,"И ещё "+(quality.warnings().size()-5)+" предупреждений.");
        tell(player,"Мир НЕ изменён. /ai confirm — построить, /ai discard — отказаться.");
        dev.aibuilder.client.AIBuilderClient.requestPreviewOpen(
            new dev.aibuilder.client.PreviewScreen.Data(
                plan.name(),quality.dimensions(),quality.blocks(),plan.operations(),
                quality.warnings(),quality.dominantMaterials()));
    }

    public void confirm(ServerPlayerEntity player) {
        Preview preview=pendingPreview;
        if(preview==null){
            tell(player,"Нет чертежа для подтверждения.");return;
        }
        if(!preview.owner().equals(player.getUuid()) ||
            preview.world()!=player.getServerWorld() ||
            !player.isCreative()) {
            tell(player,"Подтвердить может только автор в том же творческом мире.");
            return;
        }
        pendingPreview=null;
        schedule(player.getServer(),player,preview.plan());
    }

    public void discard(ServerPlayerEntity player) {
        if(pendingPreview==null){
            tell(player,"Нет ожидающего чертежа.");return;
        }
        if(!pendingPreview.owner().equals(player.getUuid())){
            tell(player,"Этот чертёж принадлежит другому игроку.");return;
        }
        pendingPreview=null;
        tell(player,"Чертёж отклонён. В мире ничего не изменено.");
    }

    public void redo(ServerPlayerEntity player){
        if(isBusy()){tell(player,"Сначала заверши или отмени текущую работу.");return;}
        if(lastRedo.isEmpty()||lastWorld!=player.getServerWorld() ||
            !player.getUuid().equals(lastOwner)){
            tell(player,"Нет безопасной постройки для повторного применения.");return;
        }
        active=new Job(lastWorld,player.getUuid(),lastRedo);
        lastRedo=List.of();
        tell(player,"Повторяю последнюю отменённую постройку, без нового API-запроса.");
    }

    public void schedule(MinecraftServer server, ServerPlayerEntity player, PlanCompiler.CompiledPlan plan) {
        if (active != null) throw new IllegalStateException("Уже идёт строительство.");
        if (server.isDedicated()) throw new IllegalArgumentException("Только одиночный мир.");
        if (!player.isCreative()) throw new IllegalArgumentException("Для строительства переключись в творческий режим.");
        if (plan.blocks().isEmpty()) throw new IllegalArgumentException("Пустой план.");
        active = new Job(player.getServerWorld(), player.getUuid(), plan);
        lastUndo.clear();
        lastRedo=List.of();
        lastWorld = null;
        lastOwner = null;
        tell(player, "Начинаю «" + plan.name() + "»: " + plan.blocks().size() + " блоков, " +
            plan.operations() + " операций. /ai cancel — остановить и откатить.");
        if (plan.adjustedOperations() > 0)
            tell(player, "Автоматически исправлены размеры " + plan.adjustedOperations()
                + " операций. Излишне большие части уменьшены до безопасных границ.");
    }

    public void cancel(ServerPlayerEntity player) {
        token++;
        planning = false;
        if (pendingPreview!=null) {
            if(pendingPreview.owner().equals(player.getUuid())){
                pendingPreview=null;
                tell(player,"Ожидающий чертёж отменён без изменения мира.");
            } else tell(player,"Это не твой чертёж.");
            return;
        }
        if (active == null) { tell(player, "Ожидание API отменено; активной стройки нет."); return; }
        if (active.isUndo()) { tell(player, "Откат уже выполняется."); return; }
        if (!active.owner.equals(player.getUuid())) { tell(player, "Стройка запущена другим игроком."); return; }
        Deque<Change> rollback = new ArrayDeque<>();
        for (Change change : active.changes) rollback.push(change);
        active = new Job(player.getServerWorld(), player.getUuid(), rollback);
        tell(player, "Стройка остановлена. Возвращаю изменённые блоки.");
    }

    public void undo(ServerPlayerEntity player) {
        if (isBusy()) { tell(player, "Сначала используй /ai cancel."); return; }
        if (lastUndo.isEmpty() || lastWorld != player.getServerWorld() || !player.getUuid().equals(lastOwner)) {
            tell(player, "Нет последней постройки для отката в этом мире.");
            return;
        }
        active = new Job(lastWorld, player.getUuid(), lastUndo);
        lastUndo = new ArrayDeque<>();
        lastRedo=List.of();
        tell(player, "Откатываю последнюю постройку.");
    }

    public void status(ServerPlayerEntity player) {
        if (planning) { tell(player, "Запрашиваю архитектурный план у API… /ai cancel для отмены."); return; }
        if(pendingPreview!=null){
            tell(player,"Чертёж «"+pendingPreview.plan().name()+"» готов: "
                +pendingPreview.quality().summary()+". /ai confirm или /ai discard.");
            return;
        }
        if (active == null) { tell(player, "Сейчас ничего не строится."); return; }
        tell(player, (active.isUndo() ? "Откат" : active.isRedo() ? "Повтор" : "Строительство") + ": " + active.done + "/" + active.total);
    }

    public void clear() {
        token++;
        planning = false;
        active = null;
        pendingPreview=null;
        lastUndo.clear();
        lastRedo=List.of();
        lastWorld = null;
        lastOwner = null;
    }

    private void tick(MinecraftServer server) {
        Job job = active;
        if (job == null) return;
        if (server.isDedicated()) { clear(); return; }
        BuilderConfig config = AIBuilderMod.config();
        int count = 0;
        while (count < config.blocksPerTick) {
            if (job.isUndo()) {
                Change change = job.undo.pollFirst();
                if (change == null) break;
                if (job.world.getBlockState(change.pos()).equals(change.placed())
                    && job.world.setBlockState(change.pos(), change.before(), 3))
                    job.successfullyUndone.add(change);
                else job.skipped++;
                job.done++; count++;
            } else if(job.isRedo()){
                Change change=job.redo.pollFirst();
                if(change==null)break;
                // Never overwrite changes the player made after undo.
                if(job.world.getBlockState(change.pos()).equals(change.before())
                    && job.world.getBlockEntity(change.pos())==null
                    && job.world.setBlockState(change.pos(),change.placed(),3))
                    job.changes.add(change);
                else job.skipped++;
                job.done++;count++;
            } else {
                PlanCompiler.Placement placement = job.queue.pollFirst();
                if (placement == null) break;
                BlockPos pos = placement.pos();
                BlockState old = job.world.getBlockState(pos);
                if (job.world.getBlockEntity(pos) != null || old.isOf(Blocks.BEDROCK) ||
                        old.isOf(Blocks.BARRIER) || old.getHardness(job.world, pos) < 0 ||
                        old.equals(placement.state())) {
                    job.skipped++;
                } else if (job.world.setBlockState(pos, placement.state(), 3)) {
                    job.changes.add(new Change(pos, old, placement.state()));
                } else job.skipped++;
                job.done++; count++;
            }
        }
        ticks++;
        if (ticks % 40 == 0) notifyOwner(server, job,
            (job.isUndo() ? "Откат" : "Строительство") + ": " + job.done + "/" + job.total);
        if (job.done >= job.total) {
            if(job.isUndo()) {
                List<Change> forward=new ArrayList<>(job.successfullyUndone);
                java.util.Collections.reverse(forward);
                lastRedo=List.copyOf(forward);
            } else {
                lastUndo.clear();
                for(Change change:job.changes)lastUndo.push(change);
                lastOwner=job.owner;
                lastWorld=job.world;
            }
            notifyOwner(server,job,(job.isUndo()?"Откат завершён"
                :job.isRedo()?"Повтор завершён":"Построено «"+job.name+"»")
                +": "+(job.done-job.skipped)+" блоков, пропущено "+job.skipped+"."
                +(job.isUndo()?" /ai redo для повторения.":" /ai undo для отмены."));
            active=null;
        }
    }

    private static void notifyOwner(MinecraftServer server, Job job, String message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.owner);
        if (player != null) tell(player, message);
    }
    public static void tell(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal("§b[AI Builder] §f" + message), false);
    }
}
