package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StageAliasesTest {
    @Test void commonModelStageLabelsMapToCorrectLayer() {
        assertEquals("foundation",StageAliases.normalize("Floor"));
        assertEquals("foundation",StageAliases.normalize("foundation"));
        assertEquals("walls",StageAliases.normalize("structure"));
        assertEquals("walls",StageAliases.normalize("frame"));
        assertEquals("roof",StageAliases.normalize("Roofing"));
        assertEquals("openings",StageAliases.normalize("door openings"));
        assertEquals("openings",StageAliases.normalize("window_openings"));
        assertEquals("details",StageAliases.normalize("decoration"));
        assertEquals("details",StageAliases.normalize("lighting"));
        assertEquals("interior",StageAliases.normalize("furniture"));
        assertEquals("interior",StageAliases.normalize("Furnishings"));
        assertEquals("landscape",StageAliases.normalize("gardens"));
        assertEquals("landscape",StageAliases.normalize("terrain"));
    }
    @Test void unsupportedSemanticStageStillRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> StageAliases.normalize("execute_command"));
        assertThrows(IllegalArgumentException.class,
            () -> StageAliases.normalize("destroy_world"));
        assertThrows(IllegalArgumentException.class,
            () -> StageAliases.normalize(null));
    }
}
