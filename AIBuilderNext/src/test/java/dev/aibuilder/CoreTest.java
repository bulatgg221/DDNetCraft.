package dev.aibuilder;
import com.google.gson.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.geometry.*;
import dev.aibuilder.validation.*;
import dev.aibuilder.artwork.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private static final Limits L=new Limits(512,30000,64,64,300);
    private static String plan(String operations){return "{\"format\":1,\"name\":\"test\",\"mode\":\"structure\",\"operations\":["+operations+"]}";}
    private static PlacementPlan compile(String op){return GeometryCompiler.compile(PlanParser.parse(plan(op),L),L,v->v);}
    @ParameterizedTest @ValueSource(ints={1,50,100,101,120,250,400,512}) void largePlans(int n){String op="{\"type\":\"block\",\"block\":\"stone\"}";assertEquals(n,compile(String.join(",",Collections.nCopies(n,op))).attempts());}
    @Test void rejects513(){assertThrows(IllegalArgumentException.class,()->compile(String.join(",",Collections.nCopies(513,"{\"type\":\"block\",\"block\":\"stone\"}"))));}
    @Test void configuredLimit(){assertThrows(IllegalArgumentException.class,()->PlanParser.parse(plan(String.join(",",Collections.nCopies(401,"{}"))),Limits.defaults()));}
    @Test void repairOnlyCompleteJson(){assertNotNull(PlanParser.parse("```json\n"+plan("{\"type\":\"block\",\"block\":\"stone\",}")+"\n```",L));assertThrows(IllegalArgumentException.class,()->PlanParser.parse(plan("{}").substring(0,40),L));assertThrows(IllegalArgumentException.class,()->PlanParser.parse(plan("{}").replace("\"format\":1","\"format\":1,\"format\":1"),L));}
    @Test void integersAreExact(){assertThrows(IllegalArgumentException.class,()->compile("{\"type\":\"block\",\"block\":\"stone\",\"x\":2147483648}"));assertThrows(IllegalArgumentException.class,()->compile("{\"type\":\"block\",\"block\":\"stone\",\"x\":1.5}"));assertEquals(1,compile("{\"type\":\"block\",\"block\":\"stone\",\"x\":\"1\"}").voxels().size());}
    @Test void boxesAndOverwrite(){assertEquals(27,compile("{\"type\":\"box\",\"block\":\"stone\",\"w\":3,\"h\":3,\"d\":3}").voxels().size());assertEquals(26,compile("{\"type\":\"hollow_box\",\"block\":\"stone\",\"w\":3,\"h\":3,\"d\":3}").voxels().size());assertEquals("air",compile("{\"type\":\"block\",\"block\":\"stone\"},{\"type\":\"block\",\"block\":\"air\"}").voxels().get(new Pos(0,0,0)).block());}
    @ParameterizedTest @ValueSource(strings={"line","cylinder","hollow_cylinder","sphere","pyramid","arch","gable_roof","roof_stairs","hip_roof","stairs_run","table","chair","tree"}) void primitives(String type){String op="{\"type\":\""+type+"\",\"block\":\"oak_planks\",\"w\":7,\"h\":3,\"d\":7,\"r\":2,\"x2\":4}";assertFalse(compile(op).voxels().isEmpty());}
    @Test void multipart(){var door=compile("{\"type\":\"door\",\"block\":\"oak_planks\"}").voxels();assertEquals(2,door.size());assertEquals("upper",door.get(new Pos(0,1,0)).properties().get("half"));var bed=compile("{\"type\":\"bed\",\"block\":\"red_bed\",\"facing\":\"east\"}").voxels();assertEquals("head",bed.get(new Pos(1,0,0)).properties().get("part"));}
    private static JsonObject artwork(int size,int rowLength,int rows){JsonObject o=new JsonObject();o.addProperty("w",size);o.addProperty("h",size);JsonObject p=new JsonObject();p.addProperty("A","white_concrete");p.addProperty("B","black_concrete");o.add("palette",p);JsonArray a=new JsonArray();for(int y=0;y<rows;y++)a.add("A".repeat(rowLength-1)+"B");o.add("rows",a);return o;}
    @ParameterizedTest @ValueSource(ints={8,16,32,64}) void artworkSizes(int size){var c=PixelArtNormalizer.parse(artwork(size,size,size));assertEquals(size,c.rows().size());assertEquals(0,c.repaired());}
    @ParameterizedTest @ValueSource(ints={31,32,33}) void artworkLocalRepair(int width){var c=PixelArtNormalizer.parse(artwork(32,width,32));assertTrue(c.rows().stream().allMatch(r->r.length()==32));assertEquals('B',c.rows().get(0).charAt(31));}
    @Test void artworkDamaged(){assertThrows(IllegalArgumentException.class,()->PixelArtNormalizer.parse(artwork(32,25,32)));assertThrows(IllegalArgumentException.class,()->PixelArtNormalizer.parse(artwork(32,32,29)));var o=artwork(32,32,32);o.getAsJsonArray("rows").set(0,new JsonPrimitive("X".repeat(32)));assertThrows(IllegalArgumentException.class,()->PixelArtNormalizer.parse(o));assertEquals(32,PixelArtNormalizer.parse(artwork(32,32,31)).height());}
    @ParameterizedTest @ValueSource(strings={"north","south","east","west"}) void artworkOrientation(String face){var o=artwork(8,8,8);o.addProperty("type","pixel_art");o.addProperty("facing",face);var result=compile(o.toString()).voxels();int[] f=GeometryCompiler.direction(face);assertEquals("black_concrete",result.get(new Pos(f[1]*7,7,-f[0]*7)).block());assertEquals(64,result.size());}
    @Test void boundsAndBudgets(){assertThrows(IllegalArgumentException.class,()->compile("{\"type\":\"box\",\"block\":\"stone\",\"w\":257}"));assertThrows(IllegalArgumentException.class,()->compile("{\"type\":\"box\",\"block\":\"stone\",\"w\":-1}"));}
    @Test void sections(){var s=List.of(new Blueprint.Section("roof",List.of("base")),new Blueprint.Section("base",List.of()));assertEquals(List.of("base","roof"),PlanParser.orderSections(s));assertThrows(IllegalArgumentException.class,()->PlanParser.orderSections(List.of(new Blueprint.Section("a",List.of("b")),new Blueprint.Section("b",List.of("a")))));}
    @Test void neverBuildingMacros(){assertThrows(IllegalArgumentException.class,()->compile("{\"type\":\"castle\",\"block\":\"stone\"}"));}
}
