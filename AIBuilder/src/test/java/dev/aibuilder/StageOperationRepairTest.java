package dev.aibuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the user's "Неизвестная форма: details" failure. */
final class StageOperationRepairTest {
    private static JsonObject op(String json){
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void repaired(String input,String geometry,String stage){
        JsonObject data=op(input);
        assertTrue(StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        assertEquals(geometry,data.get("type").getAsString());
        assertEquals(stage,data.get("stage").getAsString());
        assertFalse(StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
    }

    @Test void detailsWithSingleLanternBecomesActualBlock(){
        repaired("""
            {"type":"details","block":"lantern","x":7,"y":3,"z":9}
            ""","block","details");
    }

    @Test void detailsWithWindowPaneBecomesAnOriginalBlock(){
        repaired("""
            {"type":"details","block":"purple_stained_glass_pane",
             "x":12,"y":3,"z":6}
            ""","block","details");
    }

    @Test void detailsWithRealDoorGetsFunctionalTwoPartDoor(){
        repaired("""
            {"type":"details","block":"oak_door",
             "x":12,"y":1,"z":6,"facing":"north"}
            ""","door","details");
    }

    @Test void detailsWithExplicitArchHintDoesNotChangeGeometry(){
        JsonObject data=op("""
            {"type":"details","shape":"arch","block":"stone_bricks",
             "x":11,"y":1,"z":8,"w":5,"h":6,"d":1}
            """);
        assertTrue(StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        assertEquals("arch",data.get("type").getAsString());
        assertEquals("details",data.get("stage").getAsString());
        assertFalse(data.has("shape"));
        assertEquals(5,data.get("w").getAsInt());
        assertEquals(6,data.get("h").getAsInt());
    }

    @Test void thinDecorativeRectangleDefaultsToBoxNotGiantFullVolume(){
        repaired("""
            {"type":"details","block":"stone_bricks","x":12,"y":7,
             "z":8,"w":7,"h":1,"d":2}
            ""","box","details");
    }

    @Test void explicitCoordinateEndpointsUnambiguouslyMeanLine(){
        repaired("""
            {"type":"details","block":"stone_bricks","x":12,"y":8,"z":8,
             "x2":18,"y2":8,"z2":8}
            ""","line","details");
    }

    @Test void keepsExistingStageIfAlreadyPresent(){
        repaired("""
            {"type":"details","stage":"interior","block":"lantern",
             "x":10,"y":4,"z":9}
            ""","block","interior");
    }

    @Test void otherMisplacedStageLabelsAreSafelyNormalized(){
        repaired("""
            {"type":"openings","block":"air",
             "x":6,"y":1,"z":6,"w":3,"h":3,"d":1}
            ""","box","openings");
        repaired("""
            {"type":"interior","block":"oak_planks",
             "x":13,"y":2,"z":12}
            ""","block","interior");
        repaired("""
            {"type":"walls","block":"stone_bricks",
             "x":16,"y":2,"z":8,"w":1,"h":4,"d":10}
            ""","box","walls");
    }

    @Test void explicitMaterialAliasesPreserveRealMinecraftBlocks(){
        JsonObject op=op("""
            {"type":"details","material":"stone_bricks",
             "x":17,"y":2,"z":12}
            """);
        assertTrue(StageOperationRepair.normalize(op,MaterialCatalog.allowedIds()));
        assertEquals("block",op.get("type").getAsString());
        assertEquals("stone_bricks",op.get("block").getAsString());
    }

    @Test void nativeShapeNamesAreNeverReinterpreted(){
        JsonObject data=op("""
            {"type":"roof_stairs","stage":"roof","block":"dark_oak_stairs",
             "x":7,"y":11,"z":7,"w":15,"h":1,"d":10}
            """);
        assertFalse(StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        assertEquals("roof_stairs",data.get("type").getAsString());
    }

    @Test void stairsShapePropertyIsNotMistakenForAWholeGeometryPrimitive(){
        JsonObject data=op("""
            {"type":"details","shape":"straight","block":"oak_stairs",
             "x":12,"y":2,"z":7}
            """);
        assertTrue(StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        assertEquals("block",data.get("type").getAsString());
        assertEquals("straight",data.get("shape").getAsString());
    }

    @Test void ambiguousVolumetricDetailMustNotCreateRandomStoneCube(){
        JsonObject data=op("""
            {"type":"details","block":"stone_bricks",
             "x":6,"y":1,"z":6,"w":20,"h":12,"d":15}
            """);
        var error=assertThrows(IllegalArgumentException.class,
            ()->StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        assertTrue(error.getMessage().contains("неоднозначна"),error.getMessage());
    }

    @Test void missingCoordinatesAndMaterialMustNeverBeGuessed(){
        for(String json:new String[]{
            "{\"type\":\"details\",\"x\":1,\"y\":2,\"z\":3}",
            "{\"type\":\"details\",\"block\":\"stone_bricks\"}",
            "{\"type\":\"details\",\"block\":\"not_a_minecraft_block\","
              +"\"x\":1,\"y\":2,\"z\":3}"
        }){
            assertThrows(IllegalArgumentException.class,
                ()->StageOperationRepair.normalize(op(json),
                    MaterialCatalog.allowedIds()),json);
        }
    }

    @Test void nestedOperationListsCannotTurnIntoAnArbitrarySingleBlock(){
        JsonObject nested=op("""
            {"type":"details","block":"stone_bricks","x":2,"y":2,"z":2,
             "operations":[{"type":"box","block":"stone_bricks"}]}
            """);
        assertThrows(IllegalArgumentException.class,
            ()->StageOperationRepair.normalize(nested,MaterialCatalog.allowedIds()));
    }

    @Test void structuralAndSecuritySensitiveTypesAreNotBypassed(){
        for(String bad:new String[]{"castle","run_command","teleport"}){
            JsonObject data=op("""
                {"type":"details","block":"stone_bricks","x":2,"y":2,"z":2}
                """);
            data.addProperty("primitive",bad);
            assertThrows(IllegalArgumentException.class,
                ()->StageOperationRepair.normalize(data,MaterialCatalog.allowedIds()));
        }
    }

    @Test void actualParserRepairsTheSameJsonTheCompilerGets(){
        String incoming="""
            {"name":"Gothic Castle",
             "operations":[
                {"type":"details","block":"lantern","x":"9","y":"3","z":"7"},
                {"type":"details","shape":"arch","block":"stone_bricks",
                 "x":11,"y":1,"z":8,"w":5,"h":6,"d":1}
             ]}
            """;
        JsonObject root=JsonParser.parseString(
            PlanResponseParser.normalize(incoming)).getAsJsonObject();
        JsonObject lamp=root.getAsJsonArray("operations").get(0).getAsJsonObject();
        JsonObject arch=root.getAsJsonArray("operations").get(1).getAsJsonObject();
        assertEquals("block",lamp.get("type").getAsString());
        assertEquals("details",lamp.get("stage").getAsString());
        assertEquals(9,lamp.get("x").getAsInt());
        assertEquals("arch",arch.get("type").getAsString());
        assertEquals(5,arch.get("w").getAsInt());
    }
}
