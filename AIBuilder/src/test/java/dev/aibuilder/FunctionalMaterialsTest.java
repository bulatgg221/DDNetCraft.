package dev.aibuilder;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class FunctionalMaterialsTest {
    private static final Set<String> ALLOWED=MaterialCatalog.allowedIds();

    @Test void doorOperationCanAcceptMatchingWoodRatherThanFailing(){
        assertEquals("oak_door",FurnitureMaterials.door("oak_planks",ALLOWED));
        assertEquals("spruce_door",FurnitureMaterials.door("spruce_log",ALLOWED));
        assertEquals("dark_oak_door",FurnitureMaterials.door("dark_oak_trapdoor",ALLOWED));
        assertEquals("iron_door",FurnitureMaterials.door("iron_block",ALLOWED));
        assertEquals("mangrove_door",FurnitureMaterials.door("mangrove_planks",ALLOWED));
        assertEquals("cherry_door",FurnitureMaterials.door("cherry_planks",ALLOWED));
    }

    @Test void matchingColoredFabricGetsMatchingRealBed(){
        for(String color:MaterialCatalog.DYE_COLORS) {
            assertEquals(color+"_bed",FurnitureMaterials.bed(color+"_wool",ALLOWED));
            assertEquals(color+"_bed",FurnitureMaterials.bed(color+"_carpet",ALLOWED));
            assertEquals(color+"_bed",FurnitureMaterials.bed(color+"_concrete",ALLOWED));
        }
        assertNull(FurnitureMaterials.bed("stone_bricks",ALLOWED));
        assertNull(FurnitureMaterials.door("glass",ALLOWED));
    }

    @Test void woodenTableLegsBecomeFencesWithoutChangingOtherMaterials(){
        assertEquals("spruce_fence",FurnitureMaterials.tableLeg("spruce_planks",ALLOWED));
        assertEquals("dark_oak_fence",FurnitureMaterials.tableLeg("dark_oak_planks",ALLOWED));
        assertEquals("stone_bricks",FurnitureMaterials.tableLeg("stone_bricks",ALLOWED));
        assertEquals("oak_fence",FurnitureMaterials.tableLeg("oak_fence",ALLOWED));
    }

    @Test void woodenDoorAndBedAsBlockAreRoutedToFunctionalOperations(){
        assertEquals("door",OperationTypes.resolve("block","oak_door",ALLOWED).type());
        assertEquals("door",OperationTypes.resolve("block","spruce_door",ALLOWED).type());
        assertEquals("bed",OperationTypes.resolve("block","purple_bed",ALLOWED).type());
        assertEquals("block",OperationTypes.resolve("block","purple_stained_glass_pane",ALLOWED).type());
    }

    @Test void unsupportedFunctionalInputsStayRejected(){
        assertThrows(IllegalArgumentException.class,
            ()->FurnitureMaterials.door("tnt",ALLOWED));
        assertThrows(IllegalArgumentException.class,
            ()->FurnitureMaterials.bed("command_block",ALLOWED));
    }
}
