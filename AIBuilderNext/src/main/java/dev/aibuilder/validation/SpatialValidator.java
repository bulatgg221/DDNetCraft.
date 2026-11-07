package dev.aibuilder.validation;
import dev.aibuilder.geometry.*;
import dev.aibuilder.planning.*;
import java.util.*;
import static dev.aibuilder.planning.PlanParser.error;

/** Conservative voxel checks. Air in the plan is explicit; unknown world terrain is not assumed passable. */
public final class SpatialValidator {
    private static final int[][] SIDES={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}};
    public record Report(List<String> warnings,int reachable,int rooms,int distinctShapes) {}
    public static boolean passable(Voxel v){if(v==null)return true;String b=v.block();return b.endsWith(":air")||b.equals("air")||b.endsWith("_door")||b.endsWith("_carpet")||b.endsWith("_torch")||b.endsWith(":torch");}
    public static boolean support(Voxel v){return v!=null&&!passable(v)&&!v.block().endsWith(":water")&&!v.block().equals("water");}
    public static Report validate(PlacementPlan plan) {
        var vox=plan.voxels();var b=plan.blueprint();List<String> warnings=new ArrayList<>(plan.warnings());
        for(var e:vox.entrySet()){
            Pos p=e.getKey();Voxel v=e.getValue();String id=v.block();
            if(id.endsWith("_door")){String half=v.properties().getOrDefault("half","lower");Pos other=p.add(0,half.equals("upper")?-1:1,0);var mate=vox.get(other);if(mate==null||!mate.block().equals(id)||mate.properties().getOrDefault("half","lower").equals(half)||!Objects.equals(mate.properties().get("facing"),v.properties().get("facing")))throw error("Несогласованные половины двери: "+p);
                if(half.equals("lower")){if(!support(vox.get(p.add(0,-1,0))))throw error("У двери нет пола: "+p);int[] f=GeometryCompiler.direction(v.properties().getOrDefault("facing","north"));for(int s:new int[]{-1,1})if(!clear(vox,p.add(f[0]*s,0,f[1]*s)))throw error("Дверь перекрыта стеной: "+p);}}
            if(id.endsWith("_bed")){int[] f=GeometryCompiler.direction(v.properties().getOrDefault("facing","north"));boolean head=v.properties().getOrDefault("part","foot").equals("head");var mate=vox.get(p.add(f[0]*(head?-1:1),0,f[1]*(head?-1:1)));if(mate==null||!mate.block().equals(id)||Objects.equals(mate.properties().get("part"),v.properties().get("part"))||!Objects.equals(mate.properties().get("facing"),v.properties().get("facing")))throw error("Несогласованные части кровати: "+p);}
        }
        if(b.mode().equals("pixel_art")){long colors=vox.values().stream().map(Voxel::block).distinct().count();if(colors<4)warnings.add("Пиксель-арт использует менее четырёх цветов; визуальное качество не подтверждено.");return new Report(List.copyOf(warnings),0,0,1);}
        var entrance=b.components().stream().filter(c->c.kind().equals("entrance")&&c.access()!=null).findFirst().orElseThrow(()->error("Нужен компонент entrance с access."));
        List<Blueprint.Component> rooms=b.components().stream().filter(c->Set.of("room","hall","tower").contains(c.kind())).toList();
        if(rooms.isEmpty())throw error("План должен описывать основные помещения.");
        if(!stand(vox,entrance.access()))throw error("Вход непроходим или не имеет пола.");
        Set<Pos> reached=new HashSet<>();ArrayDeque<Pos> queue=new ArrayDeque<>();queue.add(entrance.access());reached.add(entrance.access());
        int minY=vox.keySet().stream().mapToInt(Pos::y).min().orElse(0),maxY=vox.keySet().stream().mapToInt(Pos::y).max().orElse(0)+1;
        int minX=vox.keySet().stream().mapToInt(Pos::x).min().orElse(0),maxX=vox.keySet().stream().mapToInt(Pos::x).max().orElse(0);
        int minZ=vox.keySet().stream().mapToInt(Pos::z).min().orElse(0),maxZ=vox.keySet().stream().mapToInt(Pos::z).max().orElse(0);
        while(!queue.isEmpty()){Pos p=queue.remove();for(int[] side:SIDES)for(int dy:new int[]{0,1,-1}){Pos n=p.add(side[0],dy,side[2]);if(n.x()<minX||n.x()>maxX||n.z()<minZ||n.z()>maxZ||n.y()<minY||n.y()>maxY)continue;
            if(dy==1&&!passable(vox.get(p.add(0,2,0))))continue;
            if(stand(vox,n)&&reached.add(n)){queue.add(n);if(reached.size()>500000)throw error("Превышен бюджет проверки проходов.");}}}
        for(var room:rooms){if(room.access()==null||!reached.contains(room.access()))throw error("Нет прохода от входа к помещению: "+room.id());}
        // Count connected solid islands; models must join structures or identify terrain contact.
        Set<Pos> solids=new HashSet<>();vox.forEach((p,v)->{if(support(v))solids.add(p);});int unsupported=0;
        while(!solids.isEmpty()){Pos first=solids.iterator().next();solids.remove(first);queue.add(first);int count=0;boolean grounded=false;
            while(!queue.isEmpty()){Pos p=queue.remove();count++;if(p.y()<=0)grounded=true;for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}){Pos n=p.add(d[0],d[1],d[2]);if(solids.remove(n))queue.add(n);}}
            if(!grounded&&count>=16)unsupported++;}
        if(unsupported>0)throw error("Крупные конструкции без связи с основанием: "+unsupported);
        for(var component:b.components()) {
            long count=vox.entrySet().stream().filter(e->e.getValue().component().equals(component.id())&&!passable(e.getValue())).count();
            if(Set.of("roof","tower","window","pool").contains(component.kind())&&count==0)throw error("Компонент не имеет геометрии: "+component.id());
            if(component.kind().equals("pool")&&vox.entrySet().stream().noneMatch(e->component.bounds().contains(e.getKey())&&e.getValue().block().endsWith("water")))throw error("Бассейн не содержит воды.");
        }
        Set<String> shapes=new HashSet<>();for(var op:b.operations())shapes.add(PlanParser.string(op,"type",""));
        boolean minimal=b.specification().toString().toLowerCase(Locale.ROOT).contains("minimal");
        if(shapes.size()<3&&!minimal)warnings.add("Эвристика: мало разнообразия форм. Проверьте соответствие запросу.");
        if(!b.components().stream().anyMatch(c->c.kind().equals("roof")))warnings.add("Не описан отдельный компонент крыши.");
        warnings.add("Красота и стилевое соответствие оцениваются эвристически; визуальная проверка моделью не выполнена.");
        return new Report(List.copyOf(warnings),reached.size(),rooms.size(),shapes.size());
    }
    private static boolean clear(Map<Pos,Voxel> v,Pos p){return passable(v.get(p))&&passable(v.get(p.add(0,1,0)));}
    private static boolean stand(Map<Pos,Voxel> v,Pos p){return clear(v,p)&&support(v.get(p.add(0,-1,0)));}
}
