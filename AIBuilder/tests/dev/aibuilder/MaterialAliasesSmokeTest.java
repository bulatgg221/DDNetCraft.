package dev.aibuilder;

import java.util.Set;

/** Dependency-free smoke test run by GitHub Actions before packaging the mod. */
public final class MaterialAliasesSmokeTest {
    public static void main(String[] args) {
        Set<String> palette = Set.of("oak_planks", "birch_planks", "oak_log",
            "dark_oak_planks", "birch_stairs", "birch_door", "glass_pane",
            "white_stained_glass_pane", "spruce_log", "stone_bricks", "oak_slab");
        check("light_oak_planks", "birch_planks", palette);
        check("minecraft:light_oak_planks", "birch_planks", palette);
        check("pale_oak_planks", "birch_planks", palette);
        check("light_oak_stairs", "birch_stairs", palette);
        check("light_oak_door", "birch_door", palette);
        check("pale_oak_log", "oak_log", palette);
        check("dark_wood_planks", "dark_oak_planks", palette);
        check("glass_panels", "glass_pane", palette);
        check("white_glass_pane", "white_stained_glass_pane", palette);
        check("spruce_wood", "spruce_log", palette);
        check("minecraft:oak_slab", "oak_slab", palette);
        check("stone_brick", "stone_bricks", palette);
        try {
            MaterialAliases.resolve("tnt", palette);
            throw new AssertionError("Unsafe block was not rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            MaterialAliases.resolve("unknown_magic_block", palette);
            throw new AssertionError("Unknown material was not rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("AI Builder material alias smoke tests passed.");
    }

    private static void check(String input, String expected, Set<String> palette) {
        String actual = MaterialAliases.resolve(input, palette);
        if (!expected.equals(actual)) {
            throw new AssertionError(input + " expected " + expected + ", got " + actual);
        }
    }
}
