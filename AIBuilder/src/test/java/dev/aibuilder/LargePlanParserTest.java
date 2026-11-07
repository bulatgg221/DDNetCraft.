package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real JSON parsing regression tests for model plans exceeding 100 operations. */
final class LargePlanParserTest {
    private static String plan(int count) {
        JsonObject root=new JsonObject();
        root.addProperty("name","An AI-designed cathedral with "+count+" details");
        JsonArray ops=new JsonArray();
        for(int i=0;i<count;i++) {
            JsonObject op=new JsonObject();
            op.addProperty("type","block");
            op.addProperty("stage","details");
            op.addProperty("block",i%2==0?"stone_bricks":"purple_stained_glass_pane");
            op.addProperty("x",6+i%20);
            op.addProperty("y",2+i/400);
            op.addProperty("z",6+(i/20)%20);
            ops.add(op);
        }
        root.add("operations",ops);
        return root.toString();
    }

    @Test void originalModelPlansWithMoreThanOneHundredOperationsPassParser() {
        for(int count:new int[]{101,110,120,121,175,250,399,400}){
            String normalized=assertDoesNotThrow(
                ()->PlanResponseParser.normalize(plan(count)),"operations="+count);
            JsonObject parsed=JsonParser.parseString(normalized).getAsJsonObject();
            assertEquals(count,parsed.getAsJsonArray("operations").size());
        }
    }

    @Test void maximumConfiguredLimitCanReachFiveHundredAndTwelve() {
        String normalized=assertDoesNotThrow(
            ()->PlanResponseParser.normalize(plan(BuildLimits.HARD_MAX_OPERATIONS)));
        assertEquals(BuildLimits.HARD_MAX_OPERATIONS,
            JsonParser.parseString(normalized).getAsJsonObject()
                .getAsJsonArray("operations").size());
    }

    @Test void inputsBeyondAbsoluteHardMaximumAreRejectedWithoutSilentTruncation() {
        IllegalArgumentException err=assertThrows(IllegalArgumentException.class,
            ()->PlanResponseParser.normalize(plan(513)));
        assertTrue(err.getMessage().contains("512"),err.getMessage());
    }

    @Test void userConfiguredLowerLimitCanStillBeEnforced() {
        assertDoesNotThrow(()->PlanResponseParser.normalize(plan(150)));
        assertThrows(IllegalArgumentException.class,
            ()->BuildLimits.requireConfiguredOperations(150,120));
    }
}
