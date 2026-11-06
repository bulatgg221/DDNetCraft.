package dev.aibuilder;

import java.util.Set;

/**
 * Selects only real properties on a block. Model-supplied decorative hints are optional.
 * Required, internally created door/bed/roof properties are validated separately.
 */
public final class BlockStateHints {
    private BlockStateHints() {}

    public static String compatibleProperty(String modelKey, Set<String> actualProperties) {
        String name = "slab_type".equals(modelKey) ? "type" : modelKey;
        return actualProperties.contains(name) ? name : null;
    }
}
