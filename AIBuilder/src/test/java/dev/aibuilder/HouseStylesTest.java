package dev.aibuilder;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class HouseStylesTest {
    private static JsonObject house(String style,int floors){
        JsonObject o=new JsonObject();
        o.addProperty("type","house");o.addProperty("style",style);
        o.addProperty("x",6);o.addProperty("y",0);o.addProperty("z",6);
        o.addProperty("w",17);o.addProperty("d",13);o.addProperty("floors",floors);
        o.addProperty("front","north");
        return o;
    }
    private static boolean has(List<JsonObject> plan,String type){
        return plan.stream().anyMatch(o->o.get("type").getAsString().equals(type));
    }

    @Test void sevenStylesProduceDistinctGeometryNotRecoloredCopies(){
        Set<String> plans=new HashSet<>();
        for(String style:List.of("medieval","modern","japanese","nordic",
                                 "alpine","rustic","brick")){
            List<JsonObject> ops=HouseLayouts.expand(house(style,2));
            assertTrue(ops.size()>40 && ops.size()<=100,style+" operation limit");
            String geometry=ops.stream().map(o->o.get("type").getAsString()+":"+
                o.get("stage").getAsString()+":"+
                o.get("x").getAsInt()+","+
                o.get("y").getAsInt()+","+
                o.get("z").getAsInt()+":"+
                o.get("w").getAsInt()+"x"+o.get("h").getAsInt()+"x"+
                o.get("d").getAsInt()).toList().toString();
            assertTrue(plans.add(geometry),"Style "+style+" reproduced an existing house layout");
            assertTrue(has(ops,"door"),style+" missing actual door");
            assertTrue(has(ops,"bed"),style+" missing furnished bedroom");
            assertTrue(has(ops,"stairs_run"),style+" missing staircase");
        }
    }

    @Test void roofProfilesChangeWithStyle(){
        List<JsonObject> modern=HouseLayouts.expand(house("modern",2));
        List<JsonObject> japanese=HouseLayouts.expand(house("japanese",2));
        List<JsonObject> medieval=HouseLayouts.expand(house("medieval",2));
        assertFalse(has(modern,"roof_stairs"));
        assertFalse(has(modern,"hip_roof"));
        assertTrue(has(japanese,"hip_roof"));
        assertFalse(has(japanese,"gable_fill"));
        assertTrue(has(medieval,"roof_stairs"));
        assertTrue(has(medieval,"gable_fill"));
    }

    @Test void balconyPorchAndGardenAffectActualBlockPositions(){
        JsonObject bare=house("medieval",2);
        bare.addProperty("porch",false);
        bare.addProperty("balcony",false);
        bare.addProperty("garden",false);
        bare.addProperty("chimney",false);
        int bareCount=HouseLayouts.expand(bare).size();
        bare.addProperty("porch",true);
        bare.addProperty("balcony",true);
        bare.addProperty("garden",true);
        bare.addProperty("chimney",true);
        List<JsonObject> rich=HouseLayouts.expand(bare);
        assertTrue(rich.size()>bareCount);
        assertTrue(rich.size()<=100);
        assertTrue(rich.stream().anyMatch(o->o.get("type").getAsString().equals("door")
            && o.get("y").getAsInt()==6),"Balcony must have a real exit");
    }

    @Test void allDirectionsAndStylesStayWithinOperationBudget(){
        for(String style:List.of("medieval","modern","japanese","nordic",
                                "alpine","rustic","brick")){
            for(String dir:List.of("north","south","east","west")){
                JsonObject p=house(style,2);
                p.addProperty("front",dir);
                assertTrue(HouseLayouts.expand(p).size()<=100,style+" "+dir);
            }
        }
    }
}
