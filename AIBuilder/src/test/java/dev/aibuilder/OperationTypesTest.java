package dev.aibuilder;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class OperationTypesTest {
    private static final Set<String> PALETTE = Set.of(
        "lantern", "soul_lantern", "torch", "chain", "oak_stairs",
        "dark_oak_stairs", "oak_slab", "glass_pane", "oak_door",
        "spruce_door", "red_bed", "oak_trapdoor", "oak_planks",
        "birch_planks", "oak_fence", "oak_log", "stone_bricks", "gravel",
        "grass_block", "red_carpet", "flower_pot", "potted_poppy", "poppy");

    private static void check(String type,String block,String expectedType,String expectedBlock) {
        OperationTypes.Resolved actual=OperationTypes.resolve(type,block,PALETTE);
        assertEquals(expectedType,actual.type(),"operation type for "+type);
        assertEquals(expectedBlock,actual.block(),"block for "+type);
    }

    @Test void lanternAsOperationType() {
        check("lantern",null,"block","lantern");
        check("soul_lantern",null,"block","soul_lantern");
        check("minecraft:lantern",null,"block","lantern");
        check("hanging_lantern",null,"block","lantern");
        check("Lantern",null,"block","lantern");
        check("torch",null,"block","torch");
        check("chain",null,"block","chain");
    }

    @Test void decorativeMaterialsAsTypes() {
        check("oak_stairs",null,"block","oak_stairs");
        check("glass_pane",null,"block","glass_pane");
        check("oak_slab",null,"block","oak_slab");
        check("flower_pot",null,"block","flower_pot");
        check("flowerpot",null,"block","flower_pot");
        check("pot",null,"block","flower_pot");
        check("potted_flower",null,"block","potted_poppy");
        check("flower",null,"block","poppy");
        check("red_carpet",null,"block","red_carpet");
        check("oak_trapdoor",null,"block","oak_trapdoor");
    }

    @Test void twoBlockStructuresGetDedicatedPrimitives() {
        check("oak_door",null,"door","oak_door");
        check("red_bed",null,"bed","red_bed");
        check("wooden_door",null,"door","oak_door");
    }

    @Test void namesUsedByModelForShapes() {
        check("window",null,"box","glass_pane");
        check("pillar","stone_bricks","box","stone_bricks");
        check("roof",null,"roof_stairs","dark_oak_stairs");
        check("stairs",null,"stairs_run","oak_stairs");
        check("floor",null,"box","oak_planks");
        check("light",null,"block","lantern");
        check("fence",null,"box","oak_fence");
    }

    @Test void preserveOriginalSupportedGeometry() {
        check("hollow_box","spruce_planks","hollow_box","spruce_planks");
        check("door","spruce_door","door","spruce_door");
        check("block","lantern","block","lantern");
        check("tree",null,"tree",null);
    }

    @Test void unknownLabelsRemainRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> OperationTypes.resolve("tnt",null,PALETTE));
        assertThrows(IllegalArgumentException.class,
            () -> OperationTypes.resolve("run_command",null,PALETTE));
        assertThrows(IllegalArgumentException.class,
            () -> OperationTypes.resolve("teleport",null,PALETTE));
    }
}
