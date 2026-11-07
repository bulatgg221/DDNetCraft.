package dev.aibuilder.world;
import dev.aibuilder.ai.*;
import dev.aibuilder.config.*;
import dev.aibuilder.geometry.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.validation.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.*;

public final class BuildService {
    public record Preview(PlacementPlan plan,SpatialValidator.Report report,BlockPos origin,String prompt,String model,UUID owner,ServerWorld world) {}
    public volatile String status="Готов к генерации";
    public volatile Preview preview;
    public volatile boolean planning;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"aibuilder-planner");t.setDaemon(true);return t;});
    private final ChatProvider provider=new ChatProvider();private Future<?> task;private long ticket;private ServerWorld planningWorld;private UUID planningOwner;
    public final BuildQueue queue=new BuildQueue(s->status=s);
    public final MaterialRegistry materials=new MaterialRegistry();
    public void generate(ServerPlayerEntity p,String prompt,BuilderConfig config){start(p,prompt,null,config);}
    public void load(ServerPlayerEntity p,String name,BuilderConfig c){start(p,"Загружен проект "+name,name,c);}
    private void start(ServerPlayerEntity p,String prompt,String saved,BuilderConfig config){
        if(p.getServer().isDedicated()||!p.isCreative())throw new IllegalArgumentException("Нужен одиночный творческий мир.");if(planning||queue.busy())throw new IllegalArgumentException("Предыдущая операция ещё не закончена.");
        if(prompt.isBlank()||prompt.length()>16000)throw new IllegalArgumentException("Описание: 1..16000 символов.");
        BuilderConfig c=config.copy();c.validate();String key=saved==null?ApiKeyStorage.get(c.provider):"";if(saved==null&&key.isBlank())throw new IllegalArgumentException("API-ключ не задан. Нажмите F8.");
        planning=true;preview=null;planningWorld=p.getServerWorld();planningOwner=p.getUuid();long token=++ticket;UUID owner=p.getUuid();ServerWorld world=p.getServerWorld();MinecraftServer server=p.getServer();
        var dir=p.getHorizontalFacing();BlockPos origin=p.getBlockPos().add(dir.getOffsetX()*6,-1,dir.getOffsetZ()*6).toImmutable();
        status=saved==null?"Проектирование ИИ: один платный запрос…":"Загрузка проекта без API…";
        task=executor.submit(()->{try{
            String json=saved==null?provider.request(c,key,PlannerInstructions.create(c.limits(),materials.ids()),prompt):ProjectStore.load(saved);
            Blueprint bp=PlanParser.parse(json,c.limits());PlacementPlan plan=GeometryCompiler.compile(bp,c.limits(),materials::resolve);SpatialValidator.Report report=SpatialValidator.validate(plan);
            server.execute(()->{if(token!=ticket)return;planning=false;ServerPlayerEntity current=server.getPlayerManager().getPlayer(owner);if(current==null||current.getServerWorld()!=world||!current.isCreative()){status="Результат отменён: игрок вышел или сменил измерение.";return;}preview=new Preview(plan,report,origin,prompt,c.model,owner,world);status="План готов: "+plan.voxels().size()+" блоков. F7 — предпросмотр и подтверждение.";});
        }catch(Exception e){server.execute(()->{if(token==ticket){planning=false;status=e.getMessage()==null?"Не удалось обработать план.":e.getMessage();}});}});
    }
    public void confirm(ServerPlayerEntity p){Preview v=preview;if(v==null)throw new IllegalArgumentException("Нет готового плана.");if(!v.owner().equals(p.getUuid())||v.world()!=p.getServerWorld())throw new IllegalArgumentException("План создан в другом мире или другим игроком.");queue.start(p,v.plan(),v.origin(),materials);preview=null;}
    public void cancel(){ticket++;planning=false;provider.cancel();if(task!=null)task.cancel(true);preview=null;queue.cancel();status="Операция остановлена. /ai undo — откат изменённых блоков.";}
    public void tick(MinecraftServer s,BuilderConfig c){if(planning){var p=s.getPlayerManager().getPlayer(planningOwner);if(p==null||p.getServerWorld()!=planningWorld)cancel();}if(preview!=null){var p=s.getPlayerManager().getPlayer(preview.owner());if(p==null||p.getServerWorld()!=preview.world())preview=null;}queue.tick(s,c.blocksPerTick);}
    public void clear(){cancel();queue.clear();}
}
