package dev.aibuilder.world;
import dev.aibuilder.geometry.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.validation.MaterialRegistry;
import net.minecraft.block.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.function.Consumer;

/** Server-thread-only, bounded preflight and mutations, one conflict-aware transaction. */
public final class BuildQueue {
    public record Change(BlockPos pos,BlockState before,BlockState after) {}
    private record History(ServerWorld world,UUID owner,List<Change> changes) {}
    private static final class Job {
        final ServerWorld world;final UUID owner;final List<Change> changes;final boolean restore;
        final List<Change> applied=new ArrayList<>();int checked,index;boolean preflight=true;
        Job(ServerWorld w,UUID o,List<Change> c,boolean restore){world=w;owner=o;changes=c;this.restore=restore;}
    }
    private Job active;private History undo,redo;private final Consumer<String> status;
    public BuildQueue(Consumer<String> status){this.status=status;}
    public boolean busy(){return active!=null;}
    public void start(ServerPlayerEntity player,PlacementPlan plan,BlockPos origin,MaterialRegistry registry){
        if(busy())throw new IllegalArgumentException("Строительство или откат уже выполняется.");
        checkPlayer(player);List<Change> changes=new ArrayList<>();
        for(var e:plan.voxels().entrySet()){Pos p=e.getKey();BlockPos pos=origin.add(p.x(),p.y(),p.z());changes.add(new Change(pos,null,registry.state(e.getValue())));}
        active=new Job(player.getServerWorld(),player.getUuid(),changes,false);status.accept("Проверка территории: 0/"+changes.size());
    }
    public void undo(ServerPlayerEntity p){restore(p,false);}
    public void redo(ServerPlayerEntity p){restore(p,true);}
    private void restore(ServerPlayerEntity p,boolean redoing){checkPlayer(p);if(busy())throw new IllegalArgumentException("Сначала остановите текущую операцию.");History h=redoing?redo:undo;
        if(h==null||h.world()!=p.getServerWorld()||!h.owner().equals(p.getUuid()))throw new IllegalArgumentException("Нет доступной истории в этом измерении.");
        List<Change> cs=new ArrayList<>();for(var c:h.changes())cs.add(new Change(c.pos(),redoing?c.before():c.after(),redoing?c.after():c.before()));Collections.reverse(cs);
        active=new Job(h.world(),h.owner(),cs,true);status.accept(redoing?"Проверка перед redo":"Проверка перед undo");
    }
    public void cancel(){if(active==null)return;Job j=active;if(!j.applied.isEmpty()){undo=new History(j.world,j.owner,List.copyOf(j.applied));redo=null;}active=null;status.accept("Остановлено. /ai undo — восстановить изменённые блоки.");}
    public void clear(){active=null;undo=null;redo=null;}
    public void tick(MinecraftServer server,int budget){Job j=active;if(j==null)return;
        ServerPlayerEntity owner=server.getPlayerManager().getPlayer(j.owner);
        if(owner==null||!owner.isCreative()||owner.getServerWorld()!=j.world){cancel();return;}
        long deadline=System.nanoTime()+8_000_000;int count=0;
        try{
            while(count++<budget&&System.nanoTime()<deadline){
                if(j.preflight){if(j.checked>=j.changes.size()){j.preflight=false;continue;}Change c=j.changes.get(j.checked);checkPosition(j.world,c.pos());
                    if(j.world.getBlockEntity(c.pos())!=null)throw new IllegalArgumentException("Защищённый block entity: "+c.pos().toShortString());
                    BlockState current=j.world.getBlockState(c.pos());if(current.getHardness(j.world,c.pos())<0)throw new IllegalArgumentException("Неразрушимый блок в области.");
                    if(c.before()!=null&&!current.equals(c.before()))throw new IllegalArgumentException("Мир изменён после постройки. Откат отменён во избежание потери изменений.");
                    j.changes.set(j.checked,new Change(c.pos(),current,c.after()));j.checked++;
                }else{
                    if(j.index>=j.changes.size()){complete(j);return;}Change c=j.changes.get(j.index);checkPosition(j.world,c.pos());
                    if(j.world.getBlockEntity(c.pos())!=null||!j.world.getBlockState(c.pos()).equals(c.before()))throw new IllegalArgumentException("Конфликт при размещении. Доступен откат выполненной части.");
                    if(!c.before().equals(c.after())){
                        // FORCE_STATE avoids neighbour shape cascades; no neighbour updates or drops.
                        if(!j.world.setBlockState(c.pos(),c.after(),50))throw new IllegalArgumentException("Minecraft отклонил размещение блока.");
                        j.applied.add(c);
                    }j.index++;
                }
            }
            status.accept((j.preflight?"Проверка территории: ":"Размещение: ")+(j.preflight?j.checked:j.index)+"/"+j.changes.size());
        }catch(Exception e){cancel();status.accept(e.getMessage());}
    }
    private void complete(Job j){
        if(j.restore){ // The inverse of the completed transaction always remains available.
            List<Change> inverse=j.applied.stream().map(c->new Change(c.pos(),c.after(),c.before())).toList();
            redo=new History(j.world,j.owner,inverse);undo=new History(j.world,j.owner,List.copyOf(j.applied));
        }else{undo=new History(j.world,j.owner,List.copyOf(j.applied));redo=null;}
        active=null;status.accept("Готово: изменено "+j.applied.size()+" блоков. /ai undo, /ai redo.");
    }
    private static void checkPlayer(ServerPlayerEntity p){if(p.getServer()==null||p.getServer().isDedicated()||!p.isCreative())throw new IllegalArgumentException("Нужен одиночный творческий мир.");}
    public static void checkPosition(ServerWorld w,BlockPos p){if(p.getY()<w.getBottomY()||p.getY()>=w.getTopY()||!w.getWorldBorder().contains(p))throw new IllegalArgumentException("Постройка выходит за границы мира.");if(!w.isChunkLoaded(p))throw new IllegalArgumentException("Часть постройки находится в незагруженном чанке.");}
}
