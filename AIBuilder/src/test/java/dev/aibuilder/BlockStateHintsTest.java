package dev.aibuilder;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class BlockStateHintsTest {
    @Test void slabIgnoresAxisButSupportsSlabType() {
        Set<String> slab = Set.of("type", "waterlogged");
        assertNull(BlockStateHints.compatibleProperty("axis", slab));
        assertNull(BlockStateHints.compatibleProperty("facing", slab));
        assertNull(BlockStateHints.compatibleProperty("half", slab));
        assertEquals("type", BlockStateHints.compatibleProperty("slab_type", slab));
        assertEquals("waterlogged", BlockStateHints.compatibleProperty("waterlogged", slab));
    }
    @Test void logsSupportAxisButNotFacing() {
        Set<String> log = Set.of("axis");
        assertEquals("axis", BlockStateHints.compatibleProperty("axis", log));
        assertNull(BlockStateHints.compatibleProperty("slab_type", log));
        assertNull(BlockStateHints.compatibleProperty("facing", log));
    }
    @Test void stairsSupportFacingAndHalfButNotAxis() {
        Set<String> stairs = Set.of("facing", "half", "shape", "waterlogged");
        assertEquals("facing", BlockStateHints.compatibleProperty("facing", stairs));
        assertEquals("half", BlockStateHints.compatibleProperty("half", stairs));
        assertNull(BlockStateHints.compatibleProperty("axis", stairs));
    }
    @Test void decorativeSolidBlocksIgnoreOrientation() {
        assertNull(BlockStateHints.compatibleProperty("axis", Set.of()));
        assertNull(BlockStateHints.compatibleProperty("half", Set.of()));
    }
}
