package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GeneratedPlanGuardTest {
    private static final String ORIGINAL = """
        {"name":"Unique cliffside castle","operations":[
        {"type":"box","stage":"foundation","block":"stone_bricks",
         "x":6,"y":0,"z":6,"w":31,"h":1,"d":25},
        {"type":"hollow_cylinder","stage":"walls","block":"stone_bricks",
         "x":10,"y":1,"z":10,"r":3,"h":12},
        {"type":"arch","stage":"walls","block":"stone_bricks",
         "x":17,"y":1,"z":6,"w":7,"h":8,"d":2},
        {"type":"roof_stairs","stage":"roof","block":"dark_oak_stairs",
         "x":15,"y":13,"z":14,"w":15,"h":1,"d":13},
        {"type":"box","stage":"openings","block":"air",
         "x":20,"y":1,"z":6,"w":1,"h":2,"d":1},
        {"type":"door","stage":"details","block":"spruce_door",
         "x":20,"y":1,"z":6,"facing":"north"}
        ]}
        """;

    @Test
    void independentlyDesignedPlacementOperationsAreAccepted() {
        assertEquals(ORIGINAL, GeneratedPlanGuard.requireOriginalGeometry(ORIGINAL));
    }

    @Test
    void fixedHouseAndCastleTemplatesAreRejectedForFreeformGeneration() {
        for(String type : new String[]{"house","cottage","manor","villa","farmhouse",
            "chalet","longhouse","castle","fortress","citadel","CASTLE"}) {
            String json="{\"operations\":[{\"type\":\"" + type + "\",\"w\":35,\"d\":35}]}";
            assertThrows(IllegalArgumentException.class,
                () -> GeneratedPlanGuard.requireOriginalGeometry(json),type);
        }
    }

    @Test
    void disguisingPrefabWithinRealOperationsIsAlsoRejected() {
        String mixed="{\"operations\":[{\"type\":\"box\",\"block\":\"stone_bricks\",\"x\":6},"
            + "{\"type\":\"castle\",\"w\":35,\"d\":35}]}";
        assertThrows(IllegalArgumentException.class,
            () -> GeneratedPlanGuard.requireOriginalGeometry(mixed));
    }

    @Test
    void emptyPlanCannotPretendToBeAValidGeneration() {
        assertThrows(IllegalArgumentException.class,
            () -> GeneratedPlanGuard.requireOriginalGeometry("{\"operations\":[]}"));
    }

    @Test
    void proceduralPrimitiveNamesAreNotMistakenForTemplates() {
        for (String type : new String[]{"gable_roof","hip_roof","hollow_box",
                                         "cylinder","block","door","bed"}) {
            assertFalse(GeneratedPlanGuard.usesPrebuilt(type));
        }
    }
}
