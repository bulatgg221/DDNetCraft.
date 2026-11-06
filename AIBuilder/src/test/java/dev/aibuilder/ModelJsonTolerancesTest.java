package dev.aibuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModelJsonTolerancesTest {
    @Test void coordinatesAsQuotedIntegersAreNormalizedSafely() {
        String raw="{\"operations\":[{\"type\":\"box\","
            +"\"x\":\"-12\",\"y\":\"0\",\"z\":\"3\","
            +"\"w\":\"17\",\"h\":\"6\",\"d\":\"13\"}]}";
        JsonObject op=JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().getAsJsonArray("operations").get(0).getAsJsonObject();
        assertTrue(op.getAsJsonPrimitive("x").isNumber());
        assertEquals(-12,op.get("x").getAsInt());
        assertTrue(op.getAsJsonPrimitive("w").isNumber());
        assertEquals(17,op.get("w").getAsInt());
    }

    @Test void quotedBooleanOptionalPropertiesAreNormalized() {
        String raw="{\"operations\":[{\"type\":\"house\","
            +"\"porch\":\"TRUE\",\"balcony\":\"false\"}]}";
        JsonObject op=JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().getAsJsonArray("operations").get(0).getAsJsonObject();
        assertTrue(op.getAsJsonPrimitive("porch").isBoolean());
        assertTrue(op.get("porch").getAsBoolean());
        assertFalse(op.get("balcony").getAsBoolean());
    }

    @Test void giantIntegersAndNonnumericStringsStayRejected() {
        for(String value:new String[]{"\"99999999999999999\"",
            "99999999999999999","\"ten\"","12.5","1e300"}) {
            String raw="{\"operations\":[{\"type\":\"box\",\"x\":"+value+"}]}";
            assertThrows(IllegalArgumentException.class,
                () -> PlanResponseParser.normalize(raw),value);
        }
    }

    @Test void malformedBooleanStillRejectedRatherThanGuessing() {
        String raw="{\"operations\":[{\"type\":\"house\",\"porch\":\"maybe\"}]}";
        assertThrows(IllegalArgumentException.class,() -> PlanResponseParser.normalize(raw));
    }
}
