package dev.aibuilder;

import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only player-relative walkability analysis of model-authored rooms.
 * Checks 2-block head clearance and actual support, plus plausible passages
 * between declared neighboring regions. This is a bounded heuristic:
 * it cannot know terrain/furniture outside the generated block plan.
 */
public final class WalkabilityAudit {
    private WalkabilityAudit() {}

    public record Cell(int x,int y,int z) {}
    private static final int MAX_CELLS_PER_REGION=25_000;

    public static List<String> inspectCompiled(
        List<ArchitectureSpec.Region> regions,
        PlanCompiler.CompiledPlan compiled,BlockPos origin) {
        Map<Cell,String> placed=new HashMap<>();
        for(PlanCompiler.Placement p:compiled.blocks()){
            var pos=p.pos();
            String name=Registries.BLOCK.getId(p.state().getBlock()).getPath();
            placed.put(new Cell(pos.getX()-origin.getX(),
                pos.getY()-origin.getY(),pos.getZ()-origin.getZ()),name);
        }
        return inspect(regions,placed);
    }

    public static List<String> inspect(List<ArchitectureSpec.Region> regions,
                                       Map<Cell,String> placed) {
        List<String> issues=new ArrayList<>();
        Map<String,Set<Cell>> walkable=new HashMap<>();
        Map<String,ArchitectureSpec.Region> byId=new HashMap<>();
        for(ArchitectureSpec.Region region:regions)byId.put(region.id(),region);
        for(ArchitectureSpec.Region region:regions){
            if(isNonInterior(region.kind()))continue;
            long volume=(long)region.width()*region.height()*region.depth();
            if(volume>MAX_CELLS_PER_REGION){
                issues.add("Зона "+region.id()+
                    " слишком велика для детального анализа проходимости.");
                continue;
            }
            Set<Cell> cells=new HashSet<>();
            for(int x=region.x();x<region.x()+region.width();x++)
                for(int z=region.z();z<region.z()+region.depth();z++)
                    for(int y=region.y();y<region.y()+region.height()-1;y++){
                        Cell feet=new Cell(x,y,z);
                        Cell head=new Cell(x,y+1,z);
                        Cell ground=new Cell(x,y-1,z);
                        if(isSupport(placed.get(ground)) &&
                            isPassable(placed.get(feet)) &&
                            isPassable(placed.get(head)))
                            cells.add(feet);
                    }
            walkable.put(region.id(),cells);
            if(cells.isEmpty())
                issues.add("Зона "+region.id()+
                    ": не найдено места с полом и проходом высотой 2 блока.");
        }
        // Check each declared door link only once, always including the room
        // geometry, not just semantic names or adjacent bounding boxes.
        Set<String> checked=new HashSet<>();
        for(ArchitectureSpec.Region region:regions){
            if(!walkable.containsKey(region.id()))continue;
            for(String id:region.connectedTo()){
                if(!walkable.containsKey(id)||id.equals(region.id()))continue;
                String key=region.id().compareTo(id)<0
                    ?region.id()+"/"+id:id+"/"+region.id();
                if(!checked.add(key))continue;
                var left=walkable.get(region.id());
                var right=walkable.get(id);
                if(left.isEmpty()||right.isEmpty())continue;
                if(!hasPassage(left,right,placed))
                    issues.add("Зоны "+region.id()+" и "+id+
                        ": не найден вероятный проходимый переход между этажами/комнатами.");
            }
        }
        return List.copyOf(issues);
    }

    private static boolean isNonInterior(String kind){
        String k=kind.toLowerCase(java.util.Locale.ROOT);
        return k.contains("garden")||k.contains("landscape")||
            k.contains("terrain")||k.contains("roof")||
            k.contains("outside")||k.contains("path")||
            k.contains("сад")||k.contains("двор")||k.contains("крыш");
    }

    private static boolean hasPassage(Set<Cell> left,Set<Cell> right,
                                      Map<Cell,String> voxels) {
        Set<Cell> small=left.size()<right.size()?left:right;
        Set<Cell> large=small==left?right:left;
        for(Cell cell:small){
            if(large.contains(cell))return true;
            for(int dx=-2;dx<=2;dx++)
                for(int dz=-2;dz<=2;dz++){
                    if(Math.abs(dx)+Math.abs(dz)>2)continue;
                    for(int dy=-1;dy<=1;dy++){
                        Cell adjacent=new Cell(
                            cell.x()+dx,cell.y()+dy,cell.z()+dz);
                        if(!large.contains(adjacent))continue;
                        if(Math.abs(dx)+Math.abs(dz)<=1)
                            return true;
                        // A 1-cell gap is acceptable only if an actual
                        // two-high clear passage and support are present.
                        Cell midpoint=new Cell(
                            cell.x()+dx/2,cell.y(),cell.z()+dz/2);
                        if(isSupport(voxels.get(new Cell(
                               midpoint.x(),midpoint.y()-1,midpoint.z())))
                            &&isPassable(voxels.get(midpoint))
                            &&isPassable(voxels.get(new Cell(
                                midpoint.x(),midpoint.y()+1,midpoint.z()))))
                            return true;
                    }
                }
        }
        return false;
    }

    private static boolean isSupport(String id){
        if(id==null||id.equals("air")||id.equals("water"))return false;
        if(id.endsWith("_door")||id.endsWith("_trapdoor")||
            id.endsWith("_carpet")||id.endsWith("_banner")||
            id.endsWith("_flower")||id.contains("tall_grass")||
            id.endsWith("_torch")||id.equals("lantern")||
            id.endsWith("_pane")||id.endsWith("_fence"))
            return false;
        return true;
    }

    private static boolean isPassable(String id){
        if(id==null||id.equals("air")||id.equals("water"))return true;
        if(id.endsWith("_door")&&!id.endsWith("_trapdoor"))return true;
        return id.endsWith("_carpet")||id.endsWith("_flower")||
            id.endsWith("_banner")||id.equals("tall_grass")||
            id.equals("short_grass")||id.equals("fern")||
            id.equals("large_fern")||id.equals("snow")||
            id.equals("torch")||id.equals("wall_torch");
    }
}
