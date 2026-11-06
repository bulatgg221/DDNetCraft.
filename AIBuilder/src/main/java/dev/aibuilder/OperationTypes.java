package dev.aibuilder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns common model-generated operation labels into safe, supported primitives.
 * Never accepts a new material or Minecraft command. Unrecognised labels still fail.
 */
public final class OperationTypes {
    private OperationTypes() {}

    public record Resolved(String type, String block) {}

    private static final Set<String> NATIVE = Set.of(
        "box", "hollow_box", "cylinder", "hollow_cylinder", "sphere",
        "pyramid", "gable_roof", "roof_stairs", "line", "arch", "tree",
        "block", "chair", "door", "bed", "table", "stairs_run");

    private static final Map<String, Resolved> DESCRIPTIVE = Map.ofEntries(
        Map.entry("window", new Resolved("box", "glass_pane")),
        Map.entry("window_pane", new Resolved("box", "glass_pane")),
        Map.entry("glass_window", new Resolved("box", "glass_pane")),
        Map.entry("wall", new Resolved("box", "stone_bricks")),
        Map.entry("floor", new Resolved("box", "oak_planks")),
        Map.entry("ceiling", new Resolved("box", "oak_planks")),
        Map.entry("pillar", new Resolved("box", "oak_log")),
        Map.entry("column", new Resolved("box", "oak_log")),
        Map.entry("roof", new Resolved("roof_stairs", "dark_oak_stairs")),
        Map.entry("sloped_roof", new Resolved("roof_stairs", "dark_oak_stairs")),
        Map.entry("staircase", new Resolved("stairs_run", "oak_stairs")),
        Map.entry("stairs", new Resolved("stairs_run", "oak_stairs")),
        Map.entry("light", new Resolved("block", "lantern")),
        Map.entry("lamp", new Resolved("block", "lantern")),
        Map.entry("hanging_lantern", new Resolved("block", "lantern")),
        Map.entry("fence", new Resolved("box", "oak_fence")),
        Map.entry("path", new Resolved("box", "gravel")),
        Map.entry("platform", new Resolved("box", "stone_bricks")),
        Map.entry("ground", new Resolved("box", "grass_block"))
    );

    public static Resolved resolve(String rawType, String explicitBlock, Set<String> palette) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT)
            .replace(' ', '_').replace('-', '_');
        if (NATIVE.contains(type)) return new Resolved(type, explicitBlock);
        Resolved descriptive = DESCRIPTIVE.get(type);
        if (descriptive != null) {
            String block = explicitBlock == null || explicitBlock.isBlank()
                ? descriptive.block() : explicitBlock;
            return new Resolved(descriptive.type(), block);
        }

        // Some models emit {"type":"lantern","x":...} instead of
        // {"type":"block","block":"lantern","x":...}. Only allow safe palette IDs.
        final String actualBlock;
        try {
            actualBlock = MaterialAliases.resolve(type, palette);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Неизвестная форма: " + rawType
                + " (используй type=block и block=<ID блока>).");
        }
        if (actualBlock.endsWith("_door") && !actualBlock.endsWith("_trapdoor"))
            return new Resolved("door", actualBlock);
        if (actualBlock.endsWith("_bed")) return new Resolved("bed", actualBlock);
        return new Resolved("block", actualBlock);
    }
}
