package dev.aibuilder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Repairs common model-generated (nonexistent) Minecraft block IDs without
 * accepting arbitrary blocks, changing the building palette or making another paid API call.
 */
public final class MaterialAliases {
    private MaterialAliases() {}

    private static final Map<String,String> EXPLICIT = Map.ofEntries(
        Map.entry("light_oak_planks", "birch_planks"),
        Map.entry("light_oak_wood", "oak_log"),
        Map.entry("light_oak_log", "oak_log"),
        Map.entry("light_oak_leaves", "birch_leaves"),
        Map.entry("pale_oak_planks", "birch_planks"),
        Map.entry("pale_oak_log", "oak_log"),
        Map.entry("pale_oak_wood", "oak_log"),
        Map.entry("light_wood_planks", "birch_planks"),
        Map.entry("white_wood_planks", "birch_planks"),
        Map.entry("light_wood", "birch_planks"),
        Map.entry("dark_wood_planks", "dark_oak_planks"),
        Map.entry("wooden_planks", "oak_planks"),
        Map.entry("wood_planks", "oak_planks"),
        Map.entry("wooden_door", "oak_door"),
        Map.entry("wooden_stairs", "oak_stairs"),
        Map.entry("wooden_slab", "oak_slab"),
        Map.entry("glass_panels", "glass_pane"),
        Map.entry("glass_panes", "glass_pane"),
        Map.entry("window_glass", "glass_pane"),
        Map.entry("oak_wood", "oak_log"),
        Map.entry("spruce_wood", "spruce_log"),
        Map.entry("dark_oak_wood", "dark_oak_log"),
        Map.entry("stone_brick", "stone_bricks"),
        Map.entry("mossy_stone_brick", "mossy_stone_bricks"),
        Map.entry("cracked_stone_brick", "cracked_stone_bricks"),
        Map.entry("stone_brick_block", "stone_bricks"),
        Map.entry("oak_wood_planks", "oak_planks"),
        Map.entry("spruce_wood_planks", "spruce_planks"),
        Map.entry("birch_wood_planks", "birch_planks"),
        Map.entry("dark_oak_wood_planks", "dark_oak_planks")
    );

    public static String resolve(String original, Set<String> whitelist) {
        if (original == null) throw new IllegalArgumentException("Пустое название блока");
        String name = original.trim().toLowerCase(Locale.ROOT)
            .replace(' ', '_').replace('-', '_');
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        if (!name.matches("[a-z0-9_]{1,80}")) {
            throw new IllegalArgumentException("Недопустимый ID блока: " + original);
        }
        if (whitelist.contains(name)) return name;

        String candidate = EXPLICIT.getOrDefault(name, name);
        if (whitelist.contains(candidate)) return candidate;

        // Pale oak entered Minecraft after 1.21.1, and "light oak" is not a block
        // in any standard version. Birch provides a similar light color where possible.
        if (candidate.startsWith("pale_oak_") || candidate.startsWith("light_oak_")) {
            String suffix = candidate.substring(candidate.indexOf("oak_") + 4);
            String birch = "birch_" + suffix;
            if (whitelist.contains(birch)) return birch;
            String oak = "oak_" + suffix;
            if (whitelist.contains(oak)) return oak;
        }

        // Common invent-a-block patterns from language models.
        if (candidate.endsWith("_wood_planks")) {
            String stem = candidate.substring(0, candidate.length() - "_wood_planks".length());
            String simplified = stem + "_planks";
            if (whitelist.contains(simplified)) return simplified;
        }
        if (candidate.endsWith("_wood")) {
            String simplified = candidate.substring(0, candidate.length()-5) + "_log";
            if (whitelist.contains(simplified)) return simplified;
        }
        if (candidate.endsWith("_plank")) {
            String plural = candidate + "s";
            if (whitelist.contains(plural)) return plural;
        }
        if (candidate.endsWith("_glass_pane")) {
            // Colors often appear as "white_glass_pane" instead of white_stained_glass_pane.
            String color = candidate.substring(0, candidate.length() - "_glass_pane".length());
            String pane = color + "_stained_glass_pane";
            if (whitelist.contains(pane)) return pane;
        }

        // Do not silently turn unknown materials (e.g. TNT) into construction blocks.
        // Rejected IDs can be added as safe explicit aliases after review.
        throw new IllegalArgumentException("Неподдерживаемый блок: " + original
            + " (используй существующее название из палитры Minecraft 1.21.1)");
    }
}
