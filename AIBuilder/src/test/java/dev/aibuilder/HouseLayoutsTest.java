package dev.aibuilder;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class HouseLayoutsTest {
    private static JsonObject input(int floors,String front) {
        JsonObject op = new JsonObject();
        op.addProperty("type","house");op.addProperty("x",8);
        op.addProperty("y",0);op.addProperty("z",6);
        op.addProperty("w",17);op.addProperty("d",13);
        op.addProperty("floors",floors);op.addProperty("front",front);
        return op;
    }

    private static int n(JsonObject op,String field){return op.get(field).getAsInt();}
    private static String t(JsonObject op,String field){return op.get(field).getAsString();}

    @Test void generatesBoundedHouseWithRealDoorWindowsAndGables(){
        List<JsonObject> ops=HouseLayouts.expand(input(2,"north"));
        assertTrue(ops.size()<=100);
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("gable_fill")));
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("roof_stairs")));
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("stairs_run")));
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("bed")));
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("door")));
        long windows=ops.stream().filter(o->t(o,"block").equals("glass_pane")).count();
        assertEquals(16,windows);
        assertTrue(ops.stream().allMatch(o->Set.of("foundation","walls","roof",
                "openings","details","interior","landscape").contains(t(o,"stage"))));
    }

    @Test void doorOpensOnActualBuildingWallForEachFront(){
        for(String front:List.of("north","south","west","east")){
            List<JsonObject> ops=HouseLayouts.expand(input(1,front));
            JsonObject door=ops.stream().filter(o->t(o,"type").equals("door"))
                .findFirst().orElseThrow();
            assertEquals(front,t(door,"facing"));
            assertEquals(1,n(door,"y"));
            switch(front){
                case "north" -> assertEquals(6,n(door,"z"));
                case "south" -> assertEquals(18,n(door,"z"));
                case "west" -> assertEquals(8,n(door,"x"));
                case "east" -> assertEquals(24,n(door,"x"));
            }
            // Each door must have a carved 2-high opening at exactly its position.
            assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("box")
                && t(o,"block").equals("air") && t(o,"stage").equals("openings")
                && n(o,"x")==n(door,"x") && n(o,"z")==n(door,"z")
                && n(o,"y")==n(door,"y") && n(o,"h")==2));
        }
    }

    @Test void stairsLineUpWithTheUpperFloorOpening(){
        List<JsonObject> ops=HouseLayouts.expand(input(2,"north"));
        JsonObject stair=ops.stream().filter(o->t(o,"type").equals("stairs_run"))
            .findFirst().orElseThrow();
        JsonObject hole=ops.stream().filter(o->t(o,"block").equals("air")
                && n(o,"y")==5 && n(o,"d")==5)
            .findFirst().orElseThrow();
        assertEquals(n(stair,"x"),n(hole,"x"));
        assertEquals(n(stair,"z")-4,n(hole,"z"));
        assertEquals(5,n(stair,"h"));
        assertEquals("north",t(stair,"facing"));
    }

    @Test void oneFloorNeedsNoStaircaseOrUpperBedroom(){
        List<JsonObject> ops=HouseLayouts.expand(input(1,"south"));
        assertTrue(ops.size()<100);
        assertTrue(ops.stream().noneMatch(o->t(o,"type").equals("stairs_run")));
        assertTrue(ops.stream().anyMatch(o->t(o,"type").equals("bed")), "Even one floor should be furnished.");
    }

    @Test void rejectImpossibleHouseDimensions(){
        JsonObject tiny=input(2,"north");
        tiny.addProperty("w",5);
        assertThrows(IllegalArgumentException.class,()->HouseLayouts.expand(tiny));
        JsonObject floors=input(2,"north");
        floors.addProperty("floors",4);
        assertThrows(IllegalArgumentException.class,()->HouseLayouts.expand(floors));
        JsonObject facing=input(2,"north");
        facing.addProperty("front","up");
        assertThrows(IllegalArgumentException.class,()->HouseLayouts.expand(facing));
    }
}
