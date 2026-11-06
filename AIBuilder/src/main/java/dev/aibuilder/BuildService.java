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
        final int total;
        int done, skipped;

        Job(ServerWorld world, UUID owner, PlanCompiler.CompiledPlan plan) {
            this.world = world;
            this.owner = owner;
            this.name = plan.name();
            this.queue = new ArrayDeque<>(plan.blocks());
            this.total = queue.size();
            this.undo = null;
        }
        Job(ServerWorld world, UUID owner, Deque<Change> undo) {
            this.world = world;
            this.owner = owner;
            this.name = "Отмена последней постройки";
            this.queue = null;
            this.undo = new ArrayDeque<>(undo);
            this.total = undo.size();
        }
        boolean isUndo() { return undo != null; }
    }

    private Job active;
    private Deque<Change> lastUndo = new ArrayDeque<>();
    private ServerWorld lastWorld;
    private UUID lastOwner;
    private int token;
    private boolean planning;
    private long ticks;

    public BuildService initialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        return this;
    }

    public boolean isBusy() { return planning || active != null; }
    public int beginPlanning() {
        if (isBusy()) return -1;
        planning = true;
        return ++token;
    }
    public boolean isPending(int ticket) { return planning && token == ticket; }
    public void endPlanning(int ticket) {
        if (isPending(ticket)) planning = false;
    }

    public void schedule(MinecraftServer server, ServerPlayerEntity player, PlanCompiler.CompiledPlan plan) {
        if (active != null) throw new IllegalStateException("Уже идёт строительство.");
        if (server.isDedicated()) throw new IllegalArgumentException("Только одиночный мир.");
        if (!player.isCreative()) throw new IllegalArgumentException("Для строительства переключись в творческий режим.");
        if (plan.blocks().isEmpty()) throw new IllegalArgumentException("Пустой план.");
        active = new Job(player.getServerWorld(), player.getUuid(), plan);
        lastUndo.clear();
        lastWorld = null;
        lastOwner = null;
        tell(player, "Начинаю «" + plan.name() + "»: " + plan.blocks().size() + " блоков, " +
            plan.operations() + " операций. /ai cancel — остановить и откатить.");
    }

    public void cancel(ServerPlayerEntity player) {
        token++;
        planning = false;
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
        tell(player, "Откатываю последнюю постройку.");
    }

    public void status(ServerPlayerEntity player) {
        if (planning) { tell(player, "Запрашиваю план у OpenAI API… /ai cancel для отмены."); return; }
        if (active == null) { tell(player, "Сейчас ничего не строится."); return; }
        tell(player, (active.isUndo() ? "Откат" : "Строительство") + ": " + active.done + "/" + active.total);
    }

    public void clear() {
        token++;
        planning = false;
        active = null;
        lastUndo.clear();
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
                if (job.world.getBlockState(change.pos()).equals(change.placed())) {
                    job.world.setBlockState(change.pos(), change.before(), 3);
                } else job.skipped++;
                job.done++; count++;
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
            if (!job.isUndo()) {
                lastUndo.clear();
                for (Change change : job.changes) lastUndo.push(change);
                lastOwner = job.owner;
                lastWorld = job.world;
            }
            notifyOwner(server, job, (job.isUndo() ? "Откат завершён" : "Построено «" + job.name + "»") +
                ": " + (job.done-job.skipped) + " блоков, пропущено " + job.skipped + "." +
                (job.isUndo() ? "" : " /ai undo для отмены."));
            active = null;
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
