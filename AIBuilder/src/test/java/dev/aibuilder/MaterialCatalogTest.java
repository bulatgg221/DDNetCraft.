package dev.aibuilder;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialCatalogTest {
    private static final Set<String> PALETTE=MaterialCatalog.allowedIds();

    @Test void originalPurplePaneIsExplicitlyAllowed() {
        assertTrue(PALETTE.contains("purple_stained_glass"));
        assertTrue(PALETTE.contains("purple_stained_glass_pane"));
        assertEquals("purple_stained_glass_pane",
            MaterialAliases.resolve("minecraft:purple_stained_glass_pane",PALETTE));
        assertEquals("purple_stained_glass_pane",
            OperationTypes.resolve("purple_stained_glass_pane",null,PALETTE).block());
    }

    @Test void allSixteenDyesHaveMatchingFullGlassAndGlassPane() {
        assertEquals(16,MaterialCatalog.DYE_COLORS.size());
        for(String color:MaterialCatalog.DYE_COLORS){
            assertTrue(PALETTE.contains(color+"_stained_glass"),color);
            assertTrue(PALETTE.contains(color+"_stained_glass_pane"),color);
            assertEquals(color+"_stained_glass_pane",
                MaterialAliases.resolve(color+"_glass_pane",PALETTE),color);
            assertTrue(PALETTE.contains(color+"_concrete"),color);
            assertTrue(PALETTE.contains(color+"_wool"),color);
            assertTrue(PALETTE.contains(color+"_carpet"),color);
            assertTrue(PALETTE.contains(color+"_bed"),color);
            assertTrue(PALETTE.contains(color+"_terracotta"),color);
        }
    }

    @Test void allWhitelistedWoodFamiliesIncludeMatchingStairsSlabsAndDoors() {
        assertEquals(11,MaterialCatalog.WOOD_VARIANTS.size());
        for(String wood:MaterialCatalog.WOOD_VARIANTS){
            assertTrue(PALETTE.contains(wood+"_planks"),wood);
            assertTrue(PALETTE.contains(wood+"_stairs"),wood);
            assertTrue(PALETTE.contains(wood+"_slab"),wood);
            assertTrue(PALETTE.contains(wood+"_door"),wood);
            assertTrue(PALETTE.contains(wood+"_trapdoor"),wood);
            assertEquals(wood+"_slab",
                FurnitureMaterials.tabletop(wood+"_planks",PALETTE));
            assertEquals(wood+"_stairs",
                FurnitureMaterials.stair(wood+"_planks",PALETTE));
        }
    }

    @Test void masonryFamiliesHaveMatchingTrimBlocks() {
        for(String family:List.of("stone_brick","deepslate_brick","tuff_brick",
                                 "nether_brick","brick","mud_brick","sandstone",
                                 "cobblestone","blackstone")) {
            assertTrue(PALETTE.contains(family+"_stairs"),family);
            assertTrue(PALETTE.contains(family+"_slab"),family);
        }
    }

    @Test void modelAndCompilerUseTheSameExactMaterialIdList(){
        List<String> words=List.of(MaterialCatalog.promptList().split("\\s+"));
        assertEquals(PALETTE,new HashSet<>(words));
        assertEquals(PALETTE.size(),words.size(),"Prompt must not repeat material IDs");
    }

    @Test void knownUnsafeBlocksRemainBanned(){
        for(String id:List.of("tnt","command_block","repeating_command_block",
            "chain_command_block","structure_block","barrier","spawner",
            "end_portal","end_portal_frame","nether_portal",
            "lava","fire","bedrock","light","structure_void","void_air")){
            assertFalse(PALETTE.contains(id),id);
            assertThrows(IllegalArgumentException.class,
                ()->MaterialAliases.resolve(id,PALETTE),id);
        }
    }

    @Test void commonArchitecturalBlocksNowHaveBroadCoverage(){
        for(String id:List.of(
            "purple_stained_glass_pane","cyan_stained_glass_pane",
            "lime_stained_glass_pane","magenta_stained_glass_pane",
            "cherry_planks","cherry_stairs","cherry_slab",
            "mangrove_planks","bamboo_planks","dark_prismarine_stairs",
            "mossy_stone_brick_stairs","tuff_brick_stairs",
            "polished_deepslate_stairs","polished_blackstone_brick_wall",
            "quartz_bricks","iron_block","amethyst_block","moss_block",
            "barrel","ladder","soul_lantern")){
            assertTrue(PALETTE.contains(id),id);
        }
    }
}
