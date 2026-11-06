package dev.aibuilder;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialAliasesRobustnessTest {
    private static final Set<String> ALL=MaterialCatalog.allowedIds();

    @Test void validPurplePaneAliasesAreAccepted() {
        for(String name:List.of(
            "purple_stained_glass_pane","minecraft:purple_stained_glass_pane",
            "purple_glass_pane","purple_stained_glass_panes",
            "purple_stained_glass_panel","purple_stained_pane",
            "stained_glass_pane_purple","Purple Stained Glass Pane",
            "purpl_stained_glass_pane")) {
            assertEquals("purple_stained_glass_pane",
                MaterialAliases.resolve(name,ALL),name);
        }
    }

    @Test void acceptsAllColorPaneAliasesNotJustPurple() {
        for(String color:MaterialCatalog.DYE_COLORS) {
            String expected=color+"_stained_glass_pane";
            assertEquals(expected,MaterialAliases.resolve(color+"_glass_pane",ALL));
            assertEquals(expected,MaterialAliases.resolve(color+"_stained_glass_panes",ALL));
            assertEquals(expected,MaterialAliases.resolve("minecraft:"+expected,ALL));
        }
    }

    @Test void noUnknownMaterialCanBecomeHazardousBlock() {
        for(String invalid:List.of("tnt","lava","command_block","barrier",
            "nether_portal","bedrock","structure_block","execute","unknown_block")) {
            assertThrows(IllegalArgumentException.class,
                () -> MaterialAliases.resolve(invalid,ALL),invalid);
        }
    }

    @Test void commonStairsSlabsAndGrayColorAreNormalized() {
        assertEquals("oak_stairs",MaterialAliases.resolve("oak_stair",ALL));
        assertEquals("oak_slab",MaterialAliases.resolve("oak_slabs",ALL));
        assertEquals("oak_fence",MaterialAliases.resolve("oak_fences",ALL));
        assertEquals("gray_stained_glass_pane",
            MaterialAliases.resolve("grey_stained_glass_pane",ALL));
        assertEquals("light_gray_stained_glass_pane",
            MaterialAliases.resolve("light_grey_stained_glass_pane",ALL));
    }

    @Test void neverInventNewBlockIdWhenModelOutputIsNonsense() {
        for(String invalid:List.of("purple_wool_hologram",
            "brick_teleporter","ultra_purple_glass_pane",
            "airship_engine","magic_castle_block")) {
            assertThrows(IllegalArgumentException.class,
                () -> MaterialAliases.resolve(invalid,ALL),invalid);
        }
    }
}
