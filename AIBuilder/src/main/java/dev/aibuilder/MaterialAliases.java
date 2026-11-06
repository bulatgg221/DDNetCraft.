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

    /** True only for a single insertion/deletion/replacement, no loose similarity. */
    private static boolean oneEditAway(String left,String right) {
        int a=left.length(),b=right.length();
        if(Math.abs(a-b)>1||left.equals(right))return false;
        int i=0,j=0,edits=0;
        while(i<a&&j<b) {
            if(left.charAt(i)==right.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(a>b)i++;
            else if(b>a)j++;
            else{i++;j++;}
        }
        return edits+(a-i)+(b-j)==1;
    }

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

        // Canonicalize harmless singular/plural and Minecraft color name errors.
        // Every proposed target must exist in the SAME approved palette.
        if (candidate.endsWith("_panes")) {
            String single=candidate.substring(0,candidate.length()-1);
            if(whitelist.contains(single))return single;
        }
        if (candidate.endsWith("_glass_panel")) {
            String pane=candidate.substring(0,candidate.length()-"_panel".length())+"_pane";
            if(whitelist.contains(pane))return pane;
        }
        if (candidate.endsWith("_stained_pane")) {
            String pane=candidate.substring(0,candidate.length()-"_stained_pane".length())
                +"_stained_glass_pane";
            if(whitelist.contains(pane))return pane;
        }
        if (candidate.startsWith("stained_glass_pane_")) {
            String pane=candidate.substring("stained_glass_pane_".length())
                +"_stained_glass_pane";
            if(whitelist.contains(pane))return pane;
        }
        for(String suffix: new String[]{"_stair","_slabs","_fences","_doors","_bricks"}) {
            if(!candidate.endsWith(suffix))continue;
            String normalized=candidate.endsWith("_stair")?candidate+"s":
                candidate.substring(0,candidate.length()-1);
            if(whitelist.contains(normalized))return normalized;
        }
        if (candidate.startsWith("grey_") || candidate.startsWith("light_grey_")) {
            String normalized=candidate.replace("grey_","gray_");
            if(whitelist.contains(normalized))return normalized;
        }

        // Fix a SINGLE obvious character typo only when exactly one whitelisted
        // block ID is one edit away. Ambiguous or larger guesses are rejected.
        if(candidate.length()>=8) {
            String match=null;
            for(String allowed:whitelist) {
                if(Math.abs(allowed.length()-candidate.length())>1)continue;
                if(!oneEditAway(candidate,allowed))continue;
                if(match!=null) {match=null;break;}
                match=allowed;
            }
            if(match!=null)return match;
        }


        // Do not silently turn unknown materials (e.g. TNT) into construction blocks.
        // Rejected IDs can be added as safe explicit aliases after review.
        throw new IllegalArgumentException("Неподдерживаемый блок: " + original
            + " (используй существующее название из палитры Minecraft 1.21.1)");
    }
}
