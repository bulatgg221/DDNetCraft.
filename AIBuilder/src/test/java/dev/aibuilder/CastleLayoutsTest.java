package dev.aibuilder;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CastleLayoutsTest {
    private static JsonObject param(int w,int d){
        JsonObject op=new JsonObject();op.addProperty("type","castle");
        op.addProperty("x",6);op.addProperty("y",0);op.addProperty("z",6);
        op.addProperty("w",w);op.addProperty("d",d);return op;
    }
    private static long count(List<JsonObject> ops,String type){
        return ops.stream().filter(o->o.get("type").getAsString().equals(type)).count();
    }
    @Test void thirtyFiveCastleHasWallsFourTowersAndAccessibleGate() {
        List<JsonObject> ops=CastleLayouts.expand(param(35,35));
        assertTrue(ops.size()<=100);
        assertEquals(4,count(ops,"hollow_cylinder")-4); // 4 tower walls + 4 parapets
        assertEquals(4,count(ops,"cylinder"));
        assertEquals(3,count(ops,"door")); // two gate leaves, keep entrance
        assertEquals(1,count(ops,"hollow_box")); // central keep
        assertEquals(1,count(ops,"roof_stairs"));
        assertTrue(ops.stream().anyMatch(o->o.get("block").getAsString().equals("air")
            && o.get("w").getAsInt()==2 && o.get("h").getAsInt()==4));
    }
    @Test void oversizeCastleSafelyClampsToThirtyNineFootprint() {
        List<JsonObject> ops=CastleLayouts.expand(param(200,300));
        assertTrue(ops.size()<=100);
        JsonObject foundation=ops.get(0);
        assertEquals(39,foundation.get("w").getAsInt());
        assertEquals(39,foundation.get("d").getAsInt());
    }
    @Test void shortCastleStillFitsOperationBudget() {
        assertTrue(CastleLayouts.expand(param(25,25)).size()<=100);
    }
}
