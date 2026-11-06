package dev.aibuilder;

import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class CatalogOperationAuditTest {
    private static final Set<String> PALETTE=MaterialCatalog.allowedIds();

    @Test void everyWhitelistedIdRoundTripsThroughMaterialResolver(){
        for(String block:PALETTE) {
            assertEquals(block,MaterialAliases.resolve(block,PALETTE),block);
            assertEquals(block,OperationTypes.resolve(block,null,PALETTE).block(),block);
        }
    }

    @Test void noFurnitureCoercionCanEverEmitAnUnsupportedBlock(){
        for(String block:PALETTE){
            String tabletop=FurnitureMaterials.tabletop(block,PALETTE);
            assertTrue(PALETTE.contains(tabletop),block+" -> "+tabletop);
            String stair=FurnitureMaterials.stair(block,PALETTE);
            assertTrue(stair==null || PALETTE.contains(stair),block+" -> "+stair);
            String door=FurnitureMaterials.door(block,PALETTE);
            assertTrue(door==null || PALETTE.contains(door),block+" -> "+door);
            String bed=FurnitureMaterials.bed(block,PALETTE);
            assertTrue(bed==null || PALETTE.contains(bed),block+" -> "+bed);
            String leg=FurnitureMaterials.tableLeg(block,PALETTE);
            assertTrue(PALETTE.contains(leg),block+" -> "+leg);
        }
    }

    @Test void moreMasonryVariantsNowResolveToCorrectRealBuildingShapes(){
        assertEquals("mossy_stone_brick_stairs",
            FurnitureMaterials.stair("mossy_stone_bricks",PALETTE));
        assertEquals("mud_brick_slab",
            FurnitureMaterials.tabletop("mud_bricks",PALETTE));
        assertEquals("tuff_brick_stairs",
            FurnitureMaterials.stair("tuff_bricks",PALETTE));
        assertEquals("deepslate_tile_stairs",
            FurnitureMaterials.stair("deepslate_tiles",PALETTE));
        assertEquals("purpur_stairs",
            FurnitureMaterials.stair("purpur_block",PALETTE));
        assertEquals("smooth_quartz_stairs",
            FurnitureMaterials.stair("smooth_quartz",PALETTE));
        assertEquals("blackstone_slab",
            FurnitureMaterials.tabletop("blackstone",PALETTE));
        assertEquals("polished_andesite_slab",
            FurnitureMaterials.tabletop("polished_andesite",PALETTE));
    }

    @Test void noColorPaneIsMistakenForAFunctionalDoorOrBed(){
        for(String color:MaterialCatalog.DYE_COLORS){
            String pane=color+"_stained_glass_pane";
            var resolved=OperationTypes.resolve(pane,null,PALETTE);
            assertEquals("block",resolved.type(),pane);
            assertEquals(pane,resolved.block());
            assertNull(FurnitureMaterials.door(pane,PALETTE));
            assertNull(FurnitureMaterials.bed(pane,PALETTE));
        }
    }
}
