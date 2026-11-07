package dev.aibuilder.geometry;
import com.google.gson.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.artwork.PixelArtNormalizer;
import java.util.*;
import java.util.function.UnaryOperator;
import static dev.aibuilder.planning.PlanParser.*;

/** Stateless bounded compiler. All recipes are geometric primitives, never building templates. */
public final class GeometryCompiler {
    public static final Set<String> TYPES=Set.of("block","line","box","hollow_box","cylinder","hollow_cylinder","sphere","pyramid","arch","roof_stairs","hip_roof","gable_roof","stairs_run","door","bed","table","chair","tree","pixel_art");
    private final Limits limits; private final UnaryOperator<Voxel> resolver;
    private final LinkedHashMap<Pos,Voxel> out=new LinkedHashMap<>();private final List<String> warnings=new ArrayList<>();private long attempts,visits;
    private GeometryCompiler(Limits l,UnaryOperator<Voxel> r){limits=l;resolver=r;}
    public static PlacementPlan compile(Blueprint b,Limits l,UnaryOperator<Voxel> resolver){return new GeometryCompiler(l,resolver).compile(b);}
    private PlacementPlan compile(Blueprint b) {
        if(b.operations().size()>limits.operations())throw error("Слишком много операций.");
        List<String> sectionOrder=PlanParser.orderSections(b.sections());
        Set<String> components=new HashSet<>();for(var c:b.components())components.add(c.id());
        List<JsonObject> ops=new ArrayList<>(b.operations());
        for(var op:ops){String component=string(op,"component","");if(!component.isEmpty()&&!components.contains(component))throw error("Неизвестный компонент операции: "+component);
            String section=string(op,"section","");if(!sectionOrder.isEmpty()&&!sectionOrder.contains(section))throw error("Неизвестный раздел операции.");}
        ops.sort(Comparator.comparingInt((JsonObject o)->sectionOrder.indexOf(string(o,"section",""))).thenComparingInt(o->integer(o,"stage",4)));
        for(var op:ops){if(b.mode().equals("pixel_art")&&!string(op,"type","").equals("pixel_art"))throw error("Режим pixel_art требует матрицу пикселей, а не объёмные операции.");operation(op);}
        if(out.isEmpty())throw error("Пустая геометрия.");
        return new PlacementPlan(b,out,attempts,warnings);
    }
    private void budget(){if(++visits>limits.attempts()*4)throw error("Превышен бюджет вычисления геометрии.");if((visits&1023)==0&&Thread.currentThread().isInterrupted())throw error("Компиляция отменена.");}
    private void put(Pos p,Voxel v){budget();limits.position(p);if(++attempts>limits.attempts())throw error("Превышен лимит попыток размещения.");out.remove(p);out.put(p,v);if(out.size()>limits.blocks())throw error("Превышен лимит блоков.");}
    private void put(Pos p,int x,int y,int z,Voxel v){put(p.add(x,y,z),v);}
    private Voxel material(JsonObject op,String id){Map<String,String> props=new HashMap<>();if(op.has("properties")){var ps=op.getAsJsonObject("properties");for(var e:ps.entrySet())props.put(e.getKey(),string(ps,e.getKey(),null));}
        int stage=integer(op,"stage",4);if(stage<0||stage>11)throw error("stage должен быть 0..11.");return resolver.apply(new Voxel(id,props,stage,string(op,"component","")));}
    private Voxel variant(Voxel m,String suffix){String id=m.block().replace("minecraft:","");if(!id.endsWith(suffix)){String stem=id.replaceAll("_(planks|stairs|slab|block)$","").replaceAll("bricks$","brick").replaceAll("tiles$","tile");id=stem+suffix;}
        return resolver.apply(new Voxel(id,Map.of(),m.stage(),m.component()));}
    private Voxel prop(Voxel v,String k,String s){return resolver.apply(v.with(k,s));}
    private void operation(JsonObject o) {
        String type=string(o,"type",null);if(!TYPES.contains(type))throw error("Неизвестная операция: "+type);
        Pos p=pos(o);limits.position(p);
        int w=integer(o,"w",1),h=integer(o,"h",1),d=integer(o,"d",1),r=integer(o,"r",1);
        if(w<1||h<1||d<1||w>257||h>257||d>257||r<1||r>128)throw error("Некорректные размеры операции.");
        String facing=string(o,"facing","north");int[] f=direction(facing);
        if(type.equals("pixel_art")){var c=PixelArtNormalizer.parse(o);if(c.repaired()>0)warnings.add("Исправлено строк/высот pixel_art: "+c.repaired());
            Map<Character,Voxel> colors=new HashMap<>();for(var e:c.palette().entrySet())if(!e.getValue().equals("transparent"))colors.put(e.getKey(),material(o,e.getValue()));
            for(int row=0;row<c.height();row++)for(int col=0;col<c.width();col++){Voxel v=colors.get(c.rows().get(row).charAt(col));if(v!=null)put(p,f[1]*col,c.height()-1-row,-f[0]*col,v);}return;}
        Voxel m=material(o,string(o,"block",null));
        switch(type) {
            case "block" -> put(p,m);
            case "box","hollow_box" -> {for(int x=0;x<w;x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++){budget();if(type.equals("box")||x==0||x==w-1||y==0||y==h-1||z==0||z==d-1)put(p,x,y,z,m);}}
            case "line" -> {Pos end=new Pos(integer(o,"x2",p.x()),integer(o,"y2",p.y()),integer(o,"z2",p.z()));limits.position(end);int dx=end.x()-p.x(),dy=end.y()-p.y(),dz=end.z()-p.z();int n=Math.max(1,Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz))));for(int i=0;i<=n;i++)put(p,(int)Math.round(dx*i/(double)n),(int)Math.round(dy*i/(double)n),(int)Math.round(dz*i/(double)n),m);}
            case "cylinder","hollow_cylinder","sphere" -> {int low=type.equals("sphere")?-r:0,high=type.equals("sphere")?r:h-1;for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)for(int y=low;y<=high;y++){budget();int dist=x*x+z*z+(type.equals("sphere")?y*y:0);if(dist<=r*r&&(!type.equals("hollow_cylinder")||dist>=(r-1)*(r-1)))put(p,x,y,z,m);}}
            case "pyramid" -> {if(h>(Math.min(w,d)+1)/2)throw error("Пирамида слишком высокая для основания.");for(int y=0;y<h;y++)for(int x=y;x<w-y;x++)for(int z=y;z<d-y;z++)put(p,x,y,z,m);}
            case "arch" -> {if(w<3||h<3)throw error("Арка требует w,h>=3.");double radius=(w-1)/2.0;int rise=Math.min(h-1,(int)Math.ceil(radius));int spring=h-1-rise;for(int x=0;x<w;x++){int y=spring+(int)Math.round(rise*Math.sqrt(Math.max(0,1-(x-radius)*(x-radius)/(radius*radius))));for(int z=0;z<d;z++){put(p,x,y,z,m);if(x==0||x==w-1)for(int yy=0;yy<y;yy++)put(p,x,yy,z,m);}}}
            case "gable_roof","roof_stairs","hip_roof" -> {boolean stair=!type.equals("gable_roof");if(stair)m=variant(m,"_stairs");for(int x=0;x<w;x++)for(int z=0;z<d;z++){int sx=Math.min(x,w-1-x),sz=Math.min(z,d-1-z);boolean hip=type.equals("hip_roof");int rise=hip?Math.min(sx,sz):sx;String face=hip&&sz<sx?(z<d/2?"south":"north"):(x<w/2?"east":"west");put(p,x,rise,z,stair?prop(m,"facing",face):m);}}
            case "stairs_run" -> {m=variant(m,"_stairs");Voxel step=prop(m,"facing",facing);for(int i=0;i<h;i++)for(int a=0;a<w;a++){put(p,f[0]*i-f[1]*a,i,f[1]*i+f[0]*a,step);}}
            case "door" -> {m=variant(m,"_door");m=prop(m,"facing",facing);put(p,prop(m,"half","lower"));put(p,0,1,0,prop(m,"half","upper"));}
            case "bed" -> {if(!m.block().endsWith("_bed"))throw error("bed требует блок кровати.");m=prop(m,"facing",facing);put(p,prop(m,"part","foot"));put(p,f[0],0,f[1],prop(m,"part","head"));}
            case "table" -> {Voxel top=prop(variant(m,"_slab"),"type","top"),leg=variant(m,"_fence");if(h<2)throw error("Стол требует h>=2.");for(int x=0;x<w;x++)for(int z=0;z<d;z++){put(p,x,h-1,z,top);if((x==0||x==w-1)&&(z==0||z==d-1))for(int y=0;y<h-1;y++)put(p,x,y,z,leg);}}
            case "chair" -> put(p,prop(variant(m,"_stairs"),"facing",facing));
            case "tree" -> {if(h<3||h>20||r>6)throw error("Дерево: h 3..20, r<=6.");Voxel leaves=prop(material(o,string(o,"leaves","oak_leaves")),"persistent","true");for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++)if(x*x+y*y+z*z<=r*r)put(p,x,h-1+y,z,leaves);for(int y=0;y<h;y++)put(p,0,y,0,m);}
            default -> throw error("Не реализован примитив.");
        }
    }
    public static int[] direction(String s){return switch(s){case "north"->new int[]{0,-1};case "south"->new int[]{0,1};case "east"->new int[]{1,0};case "west"->new int[]{-1,0};default->throw error("Неверное направление: "+s);};}
}
