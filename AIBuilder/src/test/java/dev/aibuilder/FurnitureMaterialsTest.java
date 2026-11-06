package dev.aibuilder;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class FurnitureMaterialsTest {
    private static final Set<String> PALETTE = Set.of(
        "oak_planks","oak_slab","oak_stairs",
        "spruce_planks","spruce_slab","spruce_stairs",
        "dark_oak_planks","dark_oak_slab","dark_oak_stairs",
        "birch_planks","birch_slab","birch_stairs",
        "stone_bricks","stone_brick_slab","stone_brick_stairs",
        "smooth_stone","smooth_stone_slab",
        "quartz_block","quartz_slab","quartz_stairs",
        "cobblestone","cobblestone_stairs",
        "glass","oak_fence","glass_pane","lantern");

    @Test void oakPlanksAsTabletopBecomeASlabAndNoLongerCauseTypePropertyError() {
        assertEquals("oak_slab",FurnitureMaterials.tabletop("oak_planks",PALETTE));
        assertEquals("spruce_slab",FurnitureMaterials.tabletop("spruce_planks",PALETTE));
        assertEquals("dark_oak_slab",FurnitureMaterials.tabletop("dark_oak_planks",PALETTE));
        assertEquals("birch_slab",FurnitureMaterials.tabletop("minecraft:birch_planks",PALETTE));
    }

    @Test void compatibleStoneAndQuartzBlocksBecomeSlabs() {
        assertEquals("stone_brick_slab",FurnitureMaterials.tabletop("stone_bricks",PALETTE));
        assertEquals("smooth_stone_slab",FurnitureMaterials.tabletop("smooth_stone",PALETTE));
        assertEquals("quartz_slab",FurnitureMaterials.tabletop("quartz_block",PALETTE));
        assertEquals("oak_slab",FurnitureMaterials.tabletop("oak_slab",PALETTE));
    }

    @Test void FullBlockWithoutMatchingSlabKeepsItsActualBlockState() {
        assertEquals("cobblestone",FurnitureMaterials.tabletop("cobblestone",PALETTE));
        assertEquals("glass",FurnitureMaterials.tabletop("glass",PALETTE));
        assertEquals("glass_pane",FurnitureMaterials.tabletop("glass_pane",PALETTE));
    }

    @Test void roofStaircaseAndChairCanUsePlankChoices() {
        assertEquals("oak_stairs",FurnitureMaterials.stair("oak_planks",PALETTE));
        assertEquals("dark_oak_stairs",FurnitureMaterials.stair("dark_oak_planks",PALETTE));
        assertEquals("spruce_stairs",FurnitureMaterials.stair("spruce_stairs",PALETTE));
        assertEquals("stone_brick_stairs",FurnitureMaterials.stair("stone_bricks",PALETTE));
        assertEquals("cobblestone_stairs",FurnitureMaterials.stair("cobblestone",PALETTE));
        assertNull(FurnitureMaterials.stair("glass",PALETTE));
    }

    @Test void mappingNeverReturnsMaterialOutsideWhitelist() {
        Set<String> limited=Set.of("oak_planks","stone_bricks");
        assertEquals("oak_planks",FurnitureMaterials.tabletop("oak_planks",limited));
        assertNull(FurnitureMaterials.stair("oak_planks",limited));
        assertThrows(IllegalArgumentException.class,
            () -> FurnitureMaterials.tabletop("tnt",PALETTE));
        assertThrows(IllegalArgumentException.class,
            () -> FurnitureMaterials.stair("command_block",PALETTE));
    }
}
