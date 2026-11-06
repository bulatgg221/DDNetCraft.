package dev.aibuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Central safe vanilla 1.21.1 material palette for both the compiler AND model.
 * Color/wood families are expanded together so adding a full glass block never
 * accidentally omits its valid pane version.
 *
 * Names are verified by PaletteRegistryTest against Minecraft's real registry.
 */
public final class MaterialCatalog {
    private MaterialCatalog() {}

    private static final String ORIGINAL = 
        "stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks cobblestone " +
        "mossy_cobblestone stone smooth_stone andesite polished_andesite deepslate_bricks " +
        "polished_deepslate calcite tuff tuff_bricks oak_planks spruce_planks dark_oak_planks " +
        "birch_planks stripped_oak_log oak_log spruce_log dark_oak_log oak_leaves spruce_leaves " +
        "birch_leaves bricks mud_bricks terracotta white_terracotta orange_terracotta red_terracotta " +
        "white_concrete gray_concrete light_gray_concrete black_concrete blue_concrete cyan_concrete " +
        "red_concrete green_concrete yellow_concrete quartz_block smooth_quartz sandstone smooth_sandstone " +
        "red_sandstone prismarine_bricks dark_prismarine sea_lantern glowstone glass white_stained_glass " +
        "blue_stained_glass light_blue_stained_glass purple_stained_glass iron_bars gold_block emerald_block " +
        "diamond_block grass_block dirt coarse_dirt gravel sand red_sand clay snow_block packed_ice " +
        "blue_ice obsidian blackstone polished_blackstone_bricks nether_bricks red_nether_bricks " +
        "warped_planks crimson_planks water air " +
        "oak_door spruce_door birch_door dark_oak_door iron_door cherry_door warped_door crimson_door " +
        "oak_stairs spruce_stairs dark_oak_stairs birch_stairs stone_brick_stairs cobblestone_stairs " +
        "deepslate_brick_stairs brick_stairs quartz_stairs sandstone_stairs polished_andesite_stairs " +
        "oak_slab spruce_slab dark_oak_slab birch_slab stone_brick_slab smooth_stone_slab " +
        "brick_slab quartz_slab sandstone_slab deepslate_brick_slab " +
        "oak_fence spruce_fence dark_oak_fence birch_fence oak_fence_gate spruce_fence_gate " +
        "oak_trapdoor spruce_trapdoor dark_oak_trapdoor birch_trapdoor iron_trapdoor " +
        "glass_pane white_stained_glass_pane blue_stained_glass_pane light_blue_stained_glass_pane " +
        "brown_stained_glass_pane black_stained_glass_pane " +
        "stone_brick_wall cobblestone_wall deepslate_brick_wall " +
        "lantern soul_lantern chain torch wall_torch redstone_lamp bookshelf crafting_table " +
        "flower_pot potted_poppy potted_dandelion potted_blue_orchid " +
        "poppy dandelion blue_orchid rose_bush peony lilac sunflower short_grass fern " +
        "red_carpet white_carpet blue_carpet green_carpet brown_carpet " +
        "red_bed white_bed blue_bed green_bed black_bed yellow_bed";
    public static final List<String> DYE_COLORS = List.of(
        "white","orange","magenta","light_blue","yellow","lime","pink","gray",
        "light_gray","cyan","purple","blue","brown","green","red","black");
    public static final List<String> WOOD_VARIANTS = List.of(
        "oak","spruce","birch","jungle","acacia","dark_oak",
        "mangrove","cherry","bamboo","crimson","warped");
    public static final List<String> LOG_WOODS = List.of(
        "oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry");

    private static final Set<String> ALL = create();

    public static Set<String> allowedIds() { return ALL; }

    private static Set<String> create() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Collections.addAll(ids,ORIGINAL.split(" "));

        // Generate every one of the 16 vanilla dye variants, including panes.
        // These are actual block IDs in Minecraft Java 1.21.1, not arbitrary names.
        for(String color : DYE_COLORS) {
            for(String suffix : List.of(
                "_stained_glass","_stained_glass_pane",
                "_concrete","_terracotta","_glazed_terracotta",
                "_wool","_carpet","_bed","_candle"))
                ids.add(color+suffix);
        }

        // All vanilla plank families have their own matching trim/doors/furniture.
        // Keep the palette and prompt generated from this one source of truth.
        for(String wood : WOOD_VARIANTS) {
            for(String suffix : List.of(
                "_planks","_stairs","_slab","_fence",
                "_fence_gate","_door","_trapdoor","_button","_pressure_plate"))
                ids.add(wood+suffix);
        }
        for(String wood : LOG_WOODS) {
            for(String suffix : List.of("_log","_wood","_leaves"))
                ids.add(wood+suffix);
            ids.add("stripped_"+wood+"_log");
            ids.add("stripped_"+wood+"_wood");
        }

        add(ids, """
            bamboo_block stripped_bamboo_block bamboo_mosaic
            bamboo_mosaic_stairs bamboo_mosaic_slab
            crimson_stem stripped_crimson_stem crimson_hyphae stripped_crimson_hyphae
            warped_stem stripped_warped_stem warped_hyphae stripped_warped_hyphae
            azalea_leaves flowering_azalea_leaves mangrove_roots muddy_mangrove_roots
            """);

        // Stone and masonry structures: include matched stairs, slabs and walls.
        add(ids, """
            granite polished_granite granite_stairs granite_slab granite_wall
            polished_granite_stairs polished_granite_slab
            diorite polished_diorite diorite_stairs diorite_slab diorite_wall
            polished_diorite_stairs polished_diorite_slab
            andesite polished_andesite andesite_stairs andesite_slab andesite_wall
            polished_andesite_stairs polished_andesite_slab
            stone stone_stairs stone_slab smooth_stone smooth_stone_slab
            cobblestone cobblestone_stairs cobblestone_slab cobblestone_wall
            mossy_cobblestone mossy_cobblestone_stairs mossy_cobblestone_slab mossy_cobblestone_wall
            stone_bricks stone_brick_stairs stone_brick_slab stone_brick_wall
            mossy_stone_bricks mossy_stone_brick_stairs mossy_stone_brick_slab mossy_stone_brick_wall
            cracked_stone_bricks chiseled_stone_bricks
            deepslate cobbled_deepslate cobbled_deepslate_stairs cobbled_deepslate_slab cobbled_deepslate_wall
            polished_deepslate polished_deepslate_stairs polished_deepslate_slab polished_deepslate_wall
            deepslate_bricks deepslate_brick_stairs deepslate_brick_slab deepslate_brick_wall
            cracked_deepslate_bricks chiseled_deepslate
            deepslate_tiles deepslate_tile_stairs deepslate_tile_slab deepslate_tile_wall cracked_deepslate_tiles
            tuff tuff_stairs tuff_slab tuff_wall
            polished_tuff polished_tuff_stairs polished_tuff_slab polished_tuff_wall
            tuff_bricks tuff_brick_stairs tuff_brick_slab tuff_brick_wall
            chiseled_tuff chiseled_tuff_bricks
            bricks brick_stairs brick_slab brick_wall
            mud_bricks mud_brick_stairs mud_brick_slab mud_brick_wall
            sandstone sandstone_stairs sandstone_slab sandstone_wall
            chiseled_sandstone cut_sandstone cut_sandstone_slab
            smooth_sandstone smooth_sandstone_stairs smooth_sandstone_slab
            red_sandstone red_sandstone_stairs red_sandstone_slab red_sandstone_wall
            chiseled_red_sandstone cut_red_sandstone cut_red_sandstone_slab
            smooth_red_sandstone smooth_red_sandstone_stairs smooth_red_sandstone_slab
            quartz_block quartz_bricks quartz_pillar chiseled_quartz_block
            quartz_stairs quartz_slab smooth_quartz smooth_quartz_stairs smooth_quartz_slab
            prismarine prismarine_stairs prismarine_slab prismarine_wall
            prismarine_bricks prismarine_brick_stairs prismarine_brick_slab
            dark_prismarine dark_prismarine_stairs dark_prismarine_slab
            blackstone blackstone_stairs blackstone_slab blackstone_wall
            polished_blackstone polished_blackstone_stairs polished_blackstone_slab polished_blackstone_wall
            polished_blackstone_bricks polished_blackstone_brick_stairs polished_blackstone_brick_slab
            polished_blackstone_brick_wall cracked_polished_blackstone_bricks
            chiseled_polished_blackstone
            nether_bricks nether_brick_stairs nether_brick_slab nether_brick_wall
            red_nether_bricks red_nether_brick_stairs red_nether_brick_slab red_nether_brick_wall
            end_stone end_stone_bricks end_stone_brick_stairs end_stone_brick_slab end_stone_brick_wall
            purpur_block purpur_pillar purpur_stairs purpur_slab
            """);

        add(ids, """
            terracotta
            grass_block dirt coarse_dirt rooted_dirt podzol mycelium
            mud packed_mud gravel sand red_sand clay snow_block ice packed_ice blue_ice
            calcite dripstone_block amethyst_block
            obsidian crying_obsidian netherrack soul_sand soul_soil
            basalt polished_basalt smooth_basalt magma_block
            sea_lantern glowstone shroomlight ochre_froglight verdant_froglight
            pearlescent_froglight redstone_lamp jack_o_lantern
            lantern soul_lantern chain torch wall_torch soul_torch soul_wall_torch
            end_rod glass glass_pane iron_bars bookshelf chiseled_bookshelf
            crafting_table furnace smoker blast_furnace barrel
            iron_block copper_block coal_block lapis_block redstone_block
            gold_block diamond_block emerald_block netherite_block
            moss_block moss_carpet hay_block bone_block
            honeycomb_block honey_block slime_block
            polished_diorite polished_granite polished_andesite
            ladder scaffolding
            smithing_table stonecutter grindstone loom lectern
            cartography_table fletching_table composter cauldron
            enchanting_table anvil chipped_anvil damaged_anvil
            campfire soul_campfire bell
            beehive bee_nest
            flower_pot potted_poppy potted_dandelion potted_blue_orchid
            poppy dandelion blue_orchid allium azure_bluet
            orange_tulip pink_tulip red_tulip white_tulip oxeye_daisy
            cornflower lily_of_the_valley wither_rose
            pink_petals short_grass fern tall_grass large_fern
            rose_bush peony lilac sunflower sugar_cane
            oak_leaves spruce_leaves birch_leaves
            water air
            """);

        // Explicit exclusions: command blocks, TNT, fluids such as lava, fire,
        // portals, unbreakables and mechanisms are deliberately NOT added.
        return Collections.unmodifiableSet(ids);
    }

    private static void add(Set<String> ids, String whitespaceDelimited) {
        Arrays.stream(whitespaceDelimited.strip().split("\\s+")).forEach(ids::add);
    }

    /** Exact palette injected into the model prompt: cannot drift from runtime. */
    public static String promptList() {
        List<String> sorted = new ArrayList<>(ALL);
        Collections.sort(sorted);
        StringBuilder result = new StringBuilder();
        for(int i=0;i<sorted.size();i++) {
            if(i>0) result.append(i%9==0?'\n':' ');
            result.append(sorted.get(i));
        }
        return result.toString();
    }
}
