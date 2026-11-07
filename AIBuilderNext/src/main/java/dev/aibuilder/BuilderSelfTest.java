package dev.aibuilder;
import dev.aibuilder.artwork.PixelArtNormalizer;
import dev.aibuilder.geometry.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.validation.MaterialRegistry;
import com.google.gson.*;
import java.util.*;

public final class BuilderSelfTest {
    public static String run(MaterialRegistry registry){int checked=0;for(String id:registry.ids()){registry.resolve(new Voxel(id,Map.of(),0,""));checked++;}
        for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"))registry.resolve(new Voxel(color+"_stained_glass_pane",Map.of(),0,""));
        int scenarios=0;for(int n:new int[]{1,50,100,101,120,250,400,512}){JsonArray ops=new JsonArray();for(int i=0;i<n;i++){JsonObject op=new JsonObject();op.addProperty("type","block");op.addProperty("block","stone");op.addProperty("x",i%32);ops.add(op);}JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("name","selftest");root.addProperty("mode","pixel_art");root.add("operations",ops);var b=PlanParser.parse(root.toString(),new Limits(512,30000,48,32,300));GeometryCompiler.compile(b,new Limits(512,30000,48,32,300),registry::resolve);scenarios++;}
        for(int length:new int[]{31,32,33}){JsonObject op=new JsonObject();op.addProperty("w",32);op.addProperty("h",32);JsonObject pal=new JsonObject();pal.addProperty("A","white_concrete");op.add("palette",pal);JsonArray rows=new JsonArray();for(int i=0;i<32;i++)rows.add("A".repeat(length));op.add("rows",rows);if(PixelArtNormalizer.parse(op).rows().get(0).length()!=32)throw new IllegalStateException("pixel rows");scenarios++;}
        try{registry.resolve(new Voxel("oak_planks",Map.of("type","top"),0,""));throw new IllegalStateException("Свойство type ошибочно разрешено.");}catch(IllegalArgumentException expected){scenarios++;}
        for(String type:List.of("block","line","box","hollow_box","cylinder","hollow_cylinder","sphere","pyramid","arch","roof_stairs","hip_roof","gable_roof","stairs_run","door","bed","table","chair","tree")) {
            JsonObject op=new JsonObject();op.addProperty("type",type);op.addProperty("block",type.equals("bed")?"red_bed":type.equals("tree")?"oak_log":"oak_planks");
            op.addProperty("w",7);op.addProperty("h",3);op.addProperty("d",7);op.addProperty("r",2);
            JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("name","smoke");root.addProperty("mode","pixel_art");JsonArray ops=new JsonArray();ops.add(op);root.add("operations",ops);
            var plan=GeometryCompiler.compile(PlanParser.parse(root.toString(),new Limits(512,30000,64,64,300)),new Limits(512,30000,64,64,300),registry::resolve);
            if(plan.voxels().isEmpty())throw new IllegalStateException("Empty "+type);scenarios++;
        }
        for(String unsafe:List.of("tnt","bedrock","lava","fire","command_block","end_portal","barrier")) {
            try{registry.resolve(new Voxel(unsafe,Map.of(),0,""));throw new IllegalStateException("Unsafe block accepted: "+unsafe);}catch(IllegalArgumentException expected){scenarios++;}
        }
        return "Проверено ID: "+checked+", сценариев: "+scenarios+". Мир и платный API не использовались.";
    }
}
