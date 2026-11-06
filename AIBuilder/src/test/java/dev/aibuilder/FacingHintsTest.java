package dev.aibuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FacingHintsTest {
    @Test void standardAndUppercaseDirectionsAreAccepted(){
        for(String direction:new String[]{"north","south","east","west"})
            assertEquals(direction,FacingHints.horizontal(direction.toUpperCase()));
        assertEquals("north",FacingHints.horizontal("n"));
        assertEquals("south",FacingHints.horizontal("s"));
        assertEquals("east",FacingHints.horizontal("positive_x"));
        assertEquals("west",FacingHints.horizontal("-x"));
    }
    @Test void invalidOptionalDirectionCannotCausePlanRejection(){
        for(String invalid:new String[]{"up","down","northwest","outside","", "RIGHT"})
            assertEquals("north",FacingHints.horizontal(invalid));
        assertEquals("north",FacingHints.horizontal(null));
    }
    @Test void malformedDoorHingeUsesSafeDefault(){
        assertEquals("right",FacingHints.hinge("RIGHT"));
        assertEquals("left",FacingHints.hinge("left"));
        assertEquals("left",FacingHints.hinge("broken"));
        assertEquals("left",FacingHints.hinge(null));
    }
}
