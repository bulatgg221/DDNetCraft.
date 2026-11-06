package dev.aibuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PlanResponseParserTest {
    @Test
    void acceptsCleanJson() {
        String raw = "{\"name\":\"Cabin\",\"operations\":[{\"type\":\"box\",\"block\":\"oak_planks\"}]}";
        assertEquals("Cabin", JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().get("name").getAsString());
    }

    @Test
    void unwrapsMarkdownAndSurroundingText() {
        String raw = """
            Here is your structure:
            ```json
            {"name":"House","operations":[{"type":"door","block":"oak_door"}]}
            ```
            """;
        assertEquals("House", JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().get("name").getAsString());
    }

    @Test
    void removesTrailingCommas() {
        String raw = "{\"name\":\"House\",\"operations\":[{\"type\":\"box\",},],}";
        JsonObject valid = JsonParser.parseString(PlanResponseParser.normalize(raw)).getAsJsonObject();
        assertEquals(1,valid.getAsJsonArray("operations").size());
    }

    @Test
    void insertsMissingCommasBetweenOperations() {
        String raw = "{\"operations\":[{\"type\":\"box\"} {\"type\":\"door\"}]}";
        assertEquals(2, JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().getAsJsonArray("operations").size());
    }

    @Test
    void insertsMissingColonAfterKnownProperty() {
        String raw = "{\"name\":\"cabin\",\"operations\":[{\"type\" \"box\",\"block\":\"oak_planks\"}]}";
        assertEquals("box", JsonParser.parseString(PlanResponseParser.normalize(raw))
            .getAsJsonObject().getAsJsonArray("operations").get(0)
            .getAsJsonObject().get("type").getAsString());
    }

    @Test
    void doesNotChangeCommasInsideStringValues() {
        String raw = "{\"name\":\"x,y and },\\\"quote\\\"\",\"operations\":[{\"type\":\"box\"}]}";
        assertEquals("x,y and },\"quote\"", JsonParser.parseString(
            PlanResponseParser.normalize(raw)).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void rejectsTruncatedJsonRatherThanBuildingPartialStructure() {
        String raw = "{\"operations\":[{\"type\":\"box\"},{\"type\":";
        assertThrows(IllegalArgumentException.class,()->PlanResponseParser.normalize(raw));
    }

    @Test
    void rejectsMissingOperations() {
        assertThrows(IllegalArgumentException.class,
            () -> PlanResponseParser.normalize("{\"name\":\"Nothing\"}"));
    }

    @Test
    void rejectsTotallyUnrepairableMalformedJson() {
        assertThrows(IllegalArgumentException.class,
            () -> PlanResponseParser.normalize("{\"operations\":[{\"type\":\"box\",\"x\":???}]}"));
    }
}
