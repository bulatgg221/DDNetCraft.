package dev.aibuilder;

import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only post-compilation checks for visual red flags. The model still
 * designs the building. This is a diagnostic pass, NOT an aesthetic oracle.
 * Never silently transforms a user's unique coordinates into a prefab.
 */
public final class BuildQualityAnalyzer {
    private BuildQualityAnalyzer() {}

    public record Assessment(int width,int height,int depth,int blocks,
                             int materialCount,int rooms,int towers,
                             List<String> dominantMaterials,List<String> warnings) {
        public String dimensions(){return width+"×"+height+"×"+depth;}
        public String summary(){
            return dimensions()+", "+blocks+" блоков, "+materialCount
                +" материалов, "+warnings.size()+" предупреждений";
        }
    }

    public static Assessment inspect(String prompt,String json,
                                     PlanCompiler.CompiledPlan plan,BlockPos origin) {
        if(plan.blocks().isEmpty())
            throw new IllegalArgumentException("Невозможно оценить пустой чертёж.");
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        Map<String,Integer> count=new HashMap<>();
        Map<BlockPos,String> positions=new HashMap<>();
        for(PlanCompiler.Placement p:plan.blocks()){
            BlockPos pos=p.pos();
            minX=Math.min(minX,pos.getX());maxX=Math.max(maxX,pos.getX());
            minY=Math.min(minY,pos.getY());maxY=Math.max(maxY,pos.getY());
            minZ=Math.min(minZ,pos.getZ());maxZ=Math.max(maxZ,pos.getZ());
            String id=Registries.BLOCK.getId(p.state().getBlock()).getPath();
            count.merge(id,1,Integer::sum);
            positions.put(pos,id);
        }
        int width=maxX-minX+1,height=maxY-minY+1,depth=maxZ-minZ+1;
        ArchitectureSpec.Parsed architecture=ArchitectureSpec.inspect(json);
        String query=prompt==null?"":prompt.toLowerCase(Locale.ROOT);
        boolean picture=ArtworkMode.isArtwork(query);
        // Portraits/images have no rooms, and must not be criticized for
        // omitting architectural region metadata.
        List<String> warnings=new ArrayList<>(
            picture?List.of():architecture.warnings());
        int effectiveBlocks=(int)count.entrySet().stream()
            .filter(e->!e.getKey().equals("air")).mapToLong(Map.Entry::getValue).sum();
        if(!picture){
            int largest=count.entrySet().stream()
                .filter(e->!e.getKey().equals("air"))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
            if(effectiveBlocks>300 && count.size()<3)
                warnings.add("Очень мало разнообразия материалов для сложной постройки.");
            if(effectiveBlocks>500 && largest*100L>effectiveBlocks*92L)
                warnings.add("Более 92% видимых блоков одного материала: возможна грубая коробка.");
            if(width>10 && depth>10 && height<=3)
                warnings.add("Большая постройка почти плоская; проверь высоту здания.");

            int doors=count.keySet().stream()
                .filter(s->s.endsWith("_door")&&!s.endsWith("_trapdoor"))
                .mapToInt(s->count.get(s)).sum();
            int roofs=count.keySet().stream()
                .filter(s->s.endsWith("_stairs")||s.endsWith("_slab"))
                .mapToInt(s->count.get(s)).sum();
            if(effectiveBlocks>150 && doors==0)
                warnings.add("Не найдено настоящей двери; проверь проходимый вход.");
            if(effectiveBlocks>300 && roofs==0 &&
                (query.contains("крыш")||query.contains("roof")))
                warnings.add("Не обнаружены ступени/плиты крыши; проверь перекрытия.");
            int requestedTowers=RequestedFeatures.towers(query);
            int requestedFloors=RequestedFeatures.floors(query);
            if(requestedTowers>0 && architecture.declaredTowers()<requestedTowers)
                warnings.add("Запрошено башен: "+requestedTowers+
                    "; в плане отмечено: "+architecture.declaredTowers()+".");
            else if((query.contains("замок")||query.contains("castle"))&&
                architecture.declaredTowers()==0)
                warnings.add("Нет размеченных башен в архитектурных зонах.");
            if(requestedFloors>1 && architecture.regions().isEmpty())
                warnings.add("Запрошено этажей: "+requestedFloors+
                    ", но модель не описала уровни помещений.");
            // Multi-level projects need a stated connection; check semantics,
            // not merely stair blocks (a staircase alone doesn't prove access).
            if((query.contains("этаж")||query.contains("floor")) &&
                architecture.regions().size()>0 &&
                architecture.regions().stream().noneMatch(r->
                    r.kind().contains("stair")||r.kind().contains("лестниц")))
                warnings.add("В плане не размечена лестничная зона между этажами.");
            // A towering wall with NO openings is a useful structural warning.
            if(effectiveBlocks>1500 && doors==0 && count.keySet().stream()
                .noneMatch(s->s.contains("glass")||s.contains("_pane")))
                warnings.add("Сложная постройка без двери и окон — вероятна замкнутая оболочка.");
        } else {
            if(width<8||height<8)
                warnings.add("Картина меньше 8×8 — очень низкая детализация.");
        }

        if(!picture && !architecture.regions().isEmpty())
            warnings.addAll(WalkabilityAudit.inspectCompiled(
                architecture.regions(),plan,origin));

        // Metadata doesn't prove that a room physically exists. Evaluate
        // that at least one occupied voxel lies inside each declared region.
        for(ArchitectureSpec.Region r:architecture.regions()){
            boolean occupied=false;
            for(BlockPos pos:positions.keySet()){
                // Semantic JSON uses player-relative coordinates;
                // placements are absolute world positions.
                if(r.contains(pos.getX()-origin.getX(),
                    pos.getY()-origin.getY(),pos.getZ()-origin.getZ())){
                    occupied=true;break;
                }
            }
            if(!occupied)
                warnings.add("В зоне "+r.id()+" не найдено блоков.");
        }
        List<String> popular=count.entrySet().stream()
            .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .limit(5).map(e->e.getKey()+" ("+e.getValue()+")").toList();
        return new Assessment(width,height,depth,plan.blocks().size(),
            count.size(),architecture.regions().size(),
            architecture.declaredTowers(),popular,List.copyOf(warnings));
    }
}
