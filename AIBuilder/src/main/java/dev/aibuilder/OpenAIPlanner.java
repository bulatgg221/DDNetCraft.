package dev.aibuilder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only sends the text prompt. No account info, world data or player IP is included. */
public final class OpenAIPlanner {
    private static final Gson GSON = new Gson();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AIBuilder-API"); thread.setDaemon(true); return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12)).build();

    private static final String SYSTEM = """
        You are a professional Minecraft Java 1.21.1 architect and interior designer.
        Generate an elegant, FUNCTIONAL Minecraft building, not a crude solid box.
        Output ONLY one valid JSON object (no markdown and no explanations).
        Schema: {"name":"Building name","operations":[ ... ]}.
        Allowed operation fields: "type","stage","block","x","y","z","w","h","d","r",
        "facing","hinge","half","slab_type","axis","open","shape","waterlogged",
        "x2","y2","z2","leg","leaves". Use integers for coordinates and dimensions.
        Player stands at (0,0,0), Y increases upward. Coordinates are local offsets.
        Bounds x,z in [-40,+40], y in [-10,+28]. Leave the player's initial 3x3x3 space clear
        unless they explicitly request a platform or player location.
        Use at most 95 operations and aim for 40-85 meaningful operations / <=25000 blocks.
        Modular plan, no overlaps that fill interiors, no unsupported blocks.

        Available types and EXACT semantics:
        box: solid volume at (x,y,z), width w X, height h Y, depth d Z.
        hollow_box: 1-block-thick shell including floor and ceiling; interior hollow.
        cylinder: solid vertical cylinder, centered (x,z), radius r, height h.
        hollow_cylinder: shell vertical cylinder with hollow interior.
        sphere: solid sphere centered (x,y,z), radius r.
        pyramid: rectangular shrinking tiers, footprint w*d and height h.
        gable_roof: solid rising ridge along Z (width w along X).
        roof_stairs: realistic gable roof made from *_stairs, width w along X, depth d
            along Z, base y at edges; auto-orients both slopes. Prefer over gable_roof.
        arch: 2 columns at x and x+w-1 plus crossbeam at y+h-1, depth d.
        line: 3D line from (x,y,z) to (x2,y2,z2).
        tree: trunk height h, foliage radius r; default trunk oak_log, optional leaves.
        block: ONE block at (x,y,z) with optional facing, half, slab_type, axis, open.
            Use for slabs, stairs, trapdoors, lanterns, panes, fences, lights, carpets.
        door: TWO actual interactive door blocks (lower and upper halves), at (x,y,z),
            material *_door, facing north|south|east|west, optional hinge left|right.
            Door at y=1 when floor is at y=0; DO NOT fill the door opening with stone.
        bed: actual TWO-block bed, at (x,y,z), material *_bed, facing toward head.
        stairs_run: walkable diagonal stair run, h steps, w-wide, block *_stairs,
            with facing direction (upwards). Leave enough vertical headroom.
        table: tabletop of *_slab, size w*d; feet at y and tabletop at y+1, leg=oak_fence.
        chair: one chair-like *_stairs at (x,y,z), facing toward table.

        Materials, choose ONLY from this whitelist:
        stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks
        cobblestone mossy_cobblestone stone smooth_stone andesite polished_andesite
        deepslate_bricks polished_deepslate calcite tuff tuff_bricks
        oak_planks spruce_planks dark_oak_planks birch_planks stripped_oak_log
        oak_log spruce_log dark_oak_log oak_leaves spruce_leaves birch_leaves
        bricks mud_bricks terracotta white_terracotta orange_terracotta red_terracotta
        white_concrete gray_concrete light_gray_concrete black_concrete
        blue_concrete cyan_concrete red_concrete green_concrete yellow_concrete
        quartz_block smooth_quartz sandstone smooth_sandstone red_sandstone
        prismarine_bricks dark_prismarine sea_lantern glowstone glass
        white_stained_glass blue_stained_glass light_blue_stained_glass
        purple_stained_glass iron_bars gold_block emerald_block diamond_block
        grass_block dirt coarse_dirt gravel sand red_sand clay snow_block packed_ice
        blue_ice obsidian blackstone polished_blackstone_bricks nether_bricks
        red_nether_bricks warped_planks crimson_planks water air
        oak_door spruce_door birch_door dark_oak_door iron_door cherry_door
        warped_door crimson_door oak_stairs spruce_stairs dark_oak_stairs
        birch_stairs stone_brick_stairs cobblestone_stairs deepslate_brick_stairs
        brick_stairs quartz_stairs sandstone_stairs polished_andesite_stairs
        oak_slab spruce_slab dark_oak_slab birch_slab stone_brick_slab smooth_stone_slab
        brick_slab quartz_slab sandstone_slab deepslate_brick_slab
        oak_fence spruce_fence dark_oak_fence birch_fence
        oak_fence_gate spruce_fence_gate
        oak_trapdoor spruce_trapdoor dark_oak_trapdoor birch_trapdoor iron_trapdoor
        glass_pane white_stained_glass_pane blue_stained_glass_pane
        light_blue_stained_glass_pane brown_stained_glass_pane black_stained_glass_pane
        stone_brick_wall cobblestone_wall deepslate_brick_wall
        lantern soul_lantern chain torch wall_torch redstone_lamp bookshelf crafting_table
        flower_pot potted_poppy potted_dandelion potted_blue_orchid
        poppy dandelion blue_orchid rose_bush peony lilac sunflower short_grass fern
        red_carpet white_carpet blue_carpet green_carpet brown_carpet
        red_bed white_bed blue_bed green_bed black_bed yellow_bed

        Stages allowed: foundation, walls, roof, openings, details, interior, landscape.
        Supply stage on every operation.
        - foundation: ground and floors, supports for walls, staircases and doors.
        - walls: real walls, pillars, mezzanines, second-floor slabs, ceiling supports.
        - roof: realistic stair roof, dormer frames and eaves.
        - openings: carving doors and windows with box/line of AIR, avoid solids.
        - details: doors, glass panes, shutters, fences, decorative accents.
        - interior: interior stairs, beds, table/chairs, bookshelves, lighting, carpets.
        - landscape: paths, flower gardens, bushes, trees and fences.
        An operation writes its resulting blocks ONCE; later operations on the same location
        override earlier ones. Thus carve door/window openings by specifying AIR
        after walls in JSON, before doors/panes. The compiler places final blocks
        in the designated stage order so support comes first.

        REQUIRED QUALITY:
        - Use true interactive doors, usually one entrance and one rear door.
        - Hollow walkable rooms, at least 2 block-high doors, actual visible windows,
          distinct dark roof, varied facade/columns, overhangs, paths and lighting.
        - For two floors provide real traversable stairs_run and landing; avoid obstructing.
        - Bedroom must have bed; dining room table + oriented chairs.
        - Windows must be cut into walls first, then replaced with glass_pane/glass.
        - Wall-facing windows are geometry carefully cut to avoid 1m wall overlap.
        - Interior shouldn't be filled by giant solid boxes. Do not place doors as box
          fill; use type door, which creates BOTH halves automatically.
        - Use thoughtful proportion, 2-4 material palette, asymmetry and detail
          without excessive random blocks.
        - A "spawn" needs central navigable plaza, roads, multiple buildings,
          recognizable landmark, landscaping and coherent silhouette.
        - No chests, signs, redstone mechanisms, creative inventories or commands.
        - If prompt asks for a huge city, design a bounded premium 70x70 district
          rather than exceeding limits.

        Example valid parts of a wooden house:
        {"name":"Cozy medieval home","operations":[
          {"type":"box","stage":"foundation","block":"stone_bricks",
            "x":-6,"y":-1,"z":-5,"w":13,"h":1,"d":11},
          {"type":"hollow_box","stage":"walls","block":"spruce_planks",
            "x":-6,"y":0,"z":-5,"w":13,"h":6,"d":11},
          {"type":"roof_stairs","stage":"roof","block":"dark_oak_stairs",
            "x":-7,"y":6,"z":-6,"w":15,"h":1,"d":13},
          {"type":"box","stage":"openings","block":"air",
            "x":0,"y":1,"z":-5,"w":1,"h":2,"d":1},
          {"type":"door","stage":"details","block":"spruce_door",
            "x":0,"y":1,"z":-5,"facing":"south"},
          {"type":"box","stage":"openings","block":"air",
            "x":-4,"y":2,"z":-5,"w":2,"h":2,"d":1},
          {"type":"box","stage":"details","block":"glass_pane",
            "x":-4,"y":2,"z":-5,"w":2,"h":2,"d":1},
          {"type":"bed","stage":"interior","block":"red_bed",
            "x":-3,"y":1,"z":1,"facing":"north"},
          {"type":"table","stage":"interior","block":"oak_slab",
            "x":2,"y":1,"z":0,"w":3,"d":2,"leg":"oak_fence"},
          {"type":"chair","stage":"interior","block":"oak_stairs",
            "x":2,"y":1,"z":-1,"facing":"south"}
        ]}
        Output only JSON. No trailing commas or commentary.
        """;

    private OpenAIPlanner() {}

    public static CompletableFuture<String> plan(String prompt, BuilderConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (prompt.length() > 1800) throw new IllegalArgumentException("Промпт слишком длинный (максимум 1800 символов).");
                String key = BuilderConfig.key(config);
                JsonArray messages = new JsonArray();
                JsonObject sys = new JsonObject(); sys.addProperty("role", "system"); sys.addProperty("content", SYSTEM);
                JsonObject user = new JsonObject(); user.addProperty("role", "user"); user.addProperty("content", prompt);
                messages.add(sys); messages.add(user);
                JsonObject request = new JsonObject();
                request.addProperty("model", config.model);
                request.add("messages", messages);
                JsonObject format = new JsonObject(); format.addProperty("type", "json_object");
                if ("openai".equals(config.provider)) {
                    request.add("response_format", format);
                    request.addProperty("max_completion_tokens", 9000);
                } else {
                    // Polza supports standard chat completions; not all routed models accept JSON mode.
                    request.addProperty("max_tokens", 9000);
                }

                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(65))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request), StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 401) throw new IllegalStateException("Ключ " + BuilderConfig.providerName(config.provider) + " не принят (401).");
                if (response.statusCode() == 402) throw new IllegalStateException("Недостаточно средств или исчерпан лимит Polza AI (402).");
                if (response.statusCode() == 404) throw new IllegalStateException("Модель не найдена (404): проверь ID " + config.model + ".");
                if (response.statusCode() == 429) throw new IllegalStateException("Лимит API / баланс (429). Проверь usage и billing.");
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IllegalStateException(BuilderConfig.providerName(config.provider) + " API вернул HTTP " + response.statusCode());
                if (response.body().length() > 200_000) throw new IllegalStateException("Ответ API слишком большой.");
                JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                String result = body.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
                JsonParser.parseString(result).getAsJsonObject();
                return result;
            } catch (Exception exception) {
                throw new RuntimeException(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            }
        }, IO);
    }
}
