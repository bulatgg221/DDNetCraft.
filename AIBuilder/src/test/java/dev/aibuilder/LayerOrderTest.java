package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LayerOrderTest {
    @Test void windowsAndDoorOpeningsOverrideWallsRegardlessOfJsonOrder() {
        assertTrue(LayerOrder.mayReplace(1,false,false,3));
        assertFalse(LayerOrder.mayReplace(3,false,false,1));
        assertTrue(LayerOrder.mayReplace(3,false,false,4));
        assertFalse(LayerOrder.mayReplace(4,false,false,1));
    }
    @Test void functionalBlockProtectsItsReservedSpace(){
        assertFalse(LayerOrder.mayReplace(4,true,false,5));
        assertFalse(LayerOrder.mayReplace(5,true,false,6));
        assertTrue(LayerOrder.mayReplace(4,true,true,4));
    }
    @Test void lastSameStagePlacementWinsAndHigherStagesWin() {
        assertTrue(LayerOrder.mayReplace(1,false,false,1));
        assertTrue(LayerOrder.mayReplace(1,false,false,2));
        assertFalse(LayerOrder.mayReplace(6,false,false,0));
    }
}
