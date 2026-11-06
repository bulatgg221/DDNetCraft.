package dev.aibuilder;

import java.util.Map;
import java.util.Set;

/**
 * Maps safe user/model-chosen full blocks onto compatible slab/stair variants.
 * Doesn't introduce blocks outside the existing allowlist.
 */
public final class FurnitureMaterials {
    private FurnitureMaterials() {}

    private static final Map<String, String> SLABS = Map.ofEntries(
        Map.entry("stone_bricks","stone_brick_slab"),
        Map.entry("bricks","brick_slab"),
        Map.entry("deepslate_bricks","deepslate_brick_slab"),
        Map.entry("smooth_stone","smooth_stone_slab"),
        Map.entry("quartz_block","quartz_slab"),
        Map.entry("smooth_quartz","quartz_slab"),
        Map.entry("sandstone","sandstone_slab")
    );

    private static final Map<String, String> STAIRS = Map.ofEntries(
        Map.entry("stone_bricks","stone_brick_stairs"),
        Map.entry("bricks","brick_stairs"),
        Map.entry("deepslate_bricks","deepslate_brick_stairs"),
        Map.entry("cobblestone","cobblestone_stairs"),
        Map.entry("quartz_block","quartz_stairs"),
        Map.entry("smooth_quartz","quartz_stairs"),
        Map.entry("sandstone","sandstone_stairs"),
        Map.entry("polished_andesite","polished_andesite_stairs")
    );

    /** Convert a wooden or masonry tabletop to a real slab where available. */
    public static String tabletop(String material, Set<String> allowed) {
        String id = MaterialAliases.resolve(material, allowed);
        if (id.endsWith("_slab")) return id;
        String replacement = variant(id, "_planks", "_slab", SLABS, allowed);
        return replacement != null ? replacement : id;
    }

    /** Only return a stair if the input can safely map to a whitelisted stair. */
    public static String stair(String material, Set<String> allowed) {
        String id = MaterialAliases.resolve(material, allowed);
        if (id.endsWith("_stairs")) return id;
        return variant(id, "_planks", "_stairs", STAIRS, allowed);
    }

    /** A door primitive can receive matching planks/logs/trapdoors from the model. */
    public static String door(String material, Set<String> allowed) {
        String id=MaterialAliases.resolve(material,allowed);
        if(id.endsWith("_door") && !id.endsWith("_trapdoor"))return id;
        String mapped=null;
        if(id.endsWith("_planks"))
            mapped=id.substring(0,id.length()-"_planks".length())+"_door";
        else if(id.endsWith("_trapdoor"))
            mapped=id.substring(0,id.length()-"_trapdoor".length())+"_door";
        else if(id.endsWith("_log"))
            mapped=id.substring(0,id.length()-"_log".length())+"_door";
        else if(id.endsWith("_wood"))
            mapped=id.substring(0,id.length()-"_wood".length())+"_door";
        else if(id.equals("iron_block"))
            mapped="iron_door";
        return mapped!=null && allowed.contains(mapped)?mapped:null;
    }

    /** A wool/carpet color can safely choose the matching full two-block bed. */
    public static String bed(String material, Set<String> allowed) {
        String id=MaterialAliases.resolve(material,allowed);
        if(id.endsWith("_bed"))return id;
        for(String color:MaterialCatalog.DYE_COLORS) {
            if(id.equals(color+"_wool") || id.equals(color+"_carpet")
                || id.equals(color+"_concrete")) {
                String bed=color+"_bed";
                return allowed.contains(bed)?bed:null;
            }
        }
        return null;
    }

    /** Table leg materials: use real fences for wood planks when possible. */
    public static String tableLeg(String material, Set<String> allowed) {
        String id=MaterialAliases.resolve(material,allowed);
        if(id.endsWith("_planks")){
            String fence=id.substring(0,id.length()-"_planks".length())+"_fence";
            if(allowed.contains(fence))return fence;
        }
        return id;
    }

    private static String variant(String id, String suffix, String outputSuffix,
                                  Map<String,String> explicit, Set<String> allowed) {
        String candidate = id.endsWith(suffix)
            ? id.substring(0, id.length()-suffix.length()) + outputSuffix
            : explicit.get(id);
        return candidate != null && allowed.contains(candidate) ? candidate : null;
    }
}
