package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PromptLimitsTest {
    @Test void supportsLongMultilineCastlePrompts(){
        assertEquals(12000,PromptLimits.MAX_CHARS);
        assertTrue(PromptLimits.valid("А".repeat(12000)));
        assertFalse(PromptLimits.valid("А".repeat(12001)));
        assertFalse(PromptLimits.valid(" "));
    }
}
