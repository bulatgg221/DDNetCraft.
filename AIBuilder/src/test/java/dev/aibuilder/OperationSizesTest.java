package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OperationSizesTest {
    @Test void oversizedCastleWallsFitWithinWorldPerimeter() {
        OperationSizes.Fitted huge=OperationSizes.fit("box",6,0,6,
            1000,500,1000,2,48,32,30000,"north");
        assertTrue(huge.adjusted());
        assertTrue(6+huge.w()-1<=48);
        assertTrue(6+huge.d()-1<=48);
        assertTrue(huge.h()-1<=32);
        assertTrue((long)huge.w()*huge.h()*huge.d()<=30000);
    }
    @Test void zeroDimensionsAreRepairedInsteadOfRejectingEntirePlan() {
        OperationSizes.Fitted fitted=OperationSizes.fit("box",3,1,3,
            0,0,-5,2,48,32,30000,"north");
        assertEquals(1,fitted.w());assertEquals(1,fitted.h());
        assertEquals(1,fitted.d());assertTrue(fitted.adjusted());
    }
    @Test void unusedDimensionsDoNotInvalidateLanternOrDoor() {
        OperationSizes.Fitted lantern=OperationSizes.fit("block",3,4,3,
            -10,-20,0,100,48,32,30000,"north");
        assertEquals(1,lantern.w());assertEquals(1,lantern.h());
        assertFalse(lantern.adjusted());
        OperationSizes.Fitted door=OperationSizes.fit("door",3,1,3,
            0,0,0,-1,48,32,30000,"north");
        assertEquals(1,door.h());
    }
    @Test void roofNearMaxHeightIsSafelyShortened() {
        OperationSizes.Fitted roof=OperationSizes.fit("roof_stairs",6,28,6,
            35,1,21,2,48,32,30000,"north");
        assertTrue(roof.w()<=9);
        assertTrue(roof.adjusted());
    }
    @Test void towerRadiusCannotCrossWorldLimit() {
        OperationSizes.Fitted tower=OperationSizes.fit("hollow_cylinder",44,1,0,
            1,12,1,30,48,32,30000,"north");
        assertEquals(4,tower.r());
        assertTrue(tower.adjusted());
    }
    @Test void stairRunStaysInsideConfiguredBounds(){
        OperationSizes.Fitted stairs=OperationSizes.fit("stairs_run",0,1,-42,
            3,30,1,2,48,32,30000,"north");
        assertEquals(7,stairs.h());
        assertEquals(3,stairs.w());
    }
    @Test void originOutsidePerimeterIsStillRejected(){
        assertThrows(IllegalArgumentException.class,()->
            OperationSizes.fit("box",300,0,0,10,10,10,2,48,32,30000,"north"));
    }
}
