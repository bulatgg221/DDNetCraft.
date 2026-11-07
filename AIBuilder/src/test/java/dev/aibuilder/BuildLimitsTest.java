package dev.aibuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BuildLimitsTest {
    @Test void oldDefaultSettingsAutomaticallyUpgradeFromOneHundredToFourHundred(){
        assertEquals(400,BuildLimits.DEFAULT_OPERATIONS);
        assertEquals(400,BuildLimits.configuredOperations(100,true));
        assertEquals(400,BuildLimits.configuredOperations(400,false));
    }

    @Test void usersCustomLimitIsRespected(){
        assertEquals(50,BuildLimits.configuredOperations(50,true));
        assertEquals(100,BuildLimits.configuredOperations(100,false));
        assertEquals(512,BuildLimits.configuredOperations(1000,false));
        assertEquals(1,BuildLimits.configuredOperations(-9,false));
    }

    @Test void detailedModelPlansAreAcceptedWithinDefaultLimit(){
        for(int n:new int[]{1,99,100,101,120,121,180,300,399,400}){
            assertDoesNotThrow(()->BuildLimits.requireParsedOperations(n), "parser "+n);
            assertDoesNotThrow(()->BuildLimits.requireConfiguredOperations(n,400),
                "compiler "+n);
        }
    }

    @Test void hardCapAndExplicitConfigRemainEffective(){
        assertDoesNotThrow(()->BuildLimits.requireParsedOperations(512));
        assertDoesNotThrow(()->BuildLimits.requireConfiguredOperations(512,512));
        assertThrows(IllegalArgumentException.class,
            ()->BuildLimits.requireParsedOperations(513));
        assertThrows(IllegalArgumentException.class,
            ()->BuildLimits.requireConfiguredOperations(401,400));
        assertThrows(IllegalArgumentException.class,
            ()->BuildLimits.requireConfiguredOperations(121,120));
        assertThrows(IllegalArgumentException.class,
            ()->BuildLimits.requireConfiguredOperations(0,400));
    }

    @Test void excessiveOverwriteAttemptsStayBounded(){
        assertEquals(360000L,BuildLimits.maxPlacementAttempts(30000));
        assertEquals(600000L,BuildLimits.maxPlacementAttempts(50000));
        assertEquals(20000L,BuildLimits.maxPlacementAttempts(100));
    }
}
