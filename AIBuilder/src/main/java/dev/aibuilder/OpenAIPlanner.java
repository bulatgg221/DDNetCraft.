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
        "x2","y2","z2","leg","leaves",
        "floors","front","wall","beam","roof","floor","foundation","door","glass",
        "style","roof_style","window_style","roof_block","porch","balcony",
        "chimney","garden".
        Use integers for coordinates and dimensions.
        Player stands at (0,0,0), Y increases upward. Coordinates are local offsets.
        Bounds x,z in [-40,+40], y in [-10,+28]. Leave the player's initial 3x3x3 space clear
        unless they explicitly request a platform or player location.
        For a single house aim for 25-50 purposeful operations, not excessive ornaments.
        For complex spawns use at most 80 operations and <=25000 blocks.
        Get the architecture and walkability RIGHT before adding minor decoration.
        JSON correctness is more important than excessive small decorations.
        Modular plan, no overlaps that fill interiors, no unsupported blocks.

        CRITICAL STYLE RULE: A "house" is NOT a fixed visual template anymore.
        It is a style-aware engine. When the user asks for Japanese, modern,
        Nordic/Viking, Alpine/chalet, medieval/Tudor, rustic forest or
        classical brick architecture, ALWAYS explicitly set house.style to
        japanese, modern, nordic, alpine, medieval, rustic or brick respectively.
        If user asks for different house designs, vary style, roof_style,
        window_style and details while keeping geometry exact.
        Do not copy a generic medieval example for every request.
        Style families:
        - medieval: exposed oak beams, gabled roof, porch, tall brick chimney
        - nordic: spruce walls, smaller windows, dark roof and chimney
        - japanese: hip roof, wide windows, graceful overhang, wooden porch/garden
        - modern: flat roof with parapet, white stone walls, wide windows, balcony
        - alpine: gabled roof, timber framing, upper balcony and entry porch
        - rustic: compact timber cottage with porch, flowerbeds, chimney
        - brick: brick facade, hip roof and formal symmetry
        User-provided dimensions and named colors/materials take precedence.
        Explicit fields: roof_style=gable|hip|flat, window_style=classic|narrow|wide|tall,
        porch/balcony/chimney/garden=true|false as JSON booleans.
        Do not invent additional roof or facade types.
        
        IMPORTANT: THE SAFE HOUSE MACRO IS THE PREFERRED WAY TO BUILD A HOUSE.
        For a standalone house, cottage, villa, manor, cabin, or townhouse, use ONE
        operation of type="house" (or "cottage"), NOT dozens of hand-calculated wall,
        window, doorway and roof coordinates. The game calculates the exact geometry
        deterministically, preventing misaligned doors, misplaced windows and stairs.
        Example response for a beautiful two-storey medieval cottage:
        {"name":"Timber Cottage","operations":[
          {"type":"house","x":6,"y":0,"z":6,"w":17,"d":13,"floors":2,
            "style":"medieval","roof_style":"gable","window_style":"classic",
            "porch":true,"chimney":true,"balcony":false,
            "front":"north","foundation":"stone_bricks","floor":"oak_planks",
            "wall":"birch_planks","beam":"stripped_oak_log",
            "roof":"dark_oak_stairs","door":"spruce_door","glass":"glass_pane"}
        ]}
        house: style-aware complete house with floor, walls, optional gable/hip/flat
            roof, 2-high real doors, coordinated windows, interior furniture and stairs
            if floors=2, plus optional porch, balcony, chimney or garden.
            Every supported style gives a DIFFERENT physical silhouette.
            Parameters: x/y/z are the MINIMUM corner
            of the house, NOT its center. If unspecified use x=6,y=0,z=6,
            w=15,d=13,floors=2,front=north,style=medieval.
            Limits w=11..25,d=9..23,floors=1..2.
            front must be north|south|east|west. Optional palette: foundation, floor,
            wall, beam, roof (MUST be *_stairs), door (MUST be *_door), glass.
            Don't add a second wall/floor/roof/door/window around this house. The
            house already includes them. The style and optional details control
            its appearance; use them instead of custom random block placements. For a house, returning ONLY the house op
            is strongly preferred: simpler JSON and much more accurate placement.
            If the user requests garden, add at most a few separate trees/flowers
            outside the house footprint, not random floating blocks.
        For non-house requests such as a fortress, bridge, statue or spawn district,
        use other primitives described below and be mindful of exact coordinates.
        Building stages determine OVERLAP PRIORITY, not the input JSON order:
        foundation < walls < roof < openings < details < interior < landscape.
        Later stages override earlier stages even when listed earlier in JSON;
        doors and beds reserve both their block positions.
        Ensure doors and windows sit exactly on the walls, floors support furniture,
        and no later decorative blocks obstruct the entrance.

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
        block: ONE block at (x,y,z) with optional facing, half, slab_type, axis, open, hanging.
            Use for slabs, stairs, trapdoors, lanterns, panes, fences, lights, carpets.
        STRICT DISTINCTION: type is the geometric OPERATION, block is the Minecraft
        MATERIAL. Do NOT set type to a block ID (lantern, torch, glass_pane,
        oak_stairs, bookshelf, oak_fence, etc.). For a lantern use EXACTLY
        {"type":"block","stage":"details","block":"lantern","x":3,"y":4,"z":0}.
        For a wall lamp use type=block and block=lantern or soul_lantern;
        hanging=true is optional when the lantern attaches below a ceiling.
        A two-block door is type=door with block=oak_door, NOT type=oak_door.
        For multiple adjacent glass panes use type=box with block=glass_pane.
        Block state properties MUST match the actual block:
        - axis=x|y|z is ONLY for orientable logs and pillar blocks.
        - smooth_stone_slab and all *_slab blocks have slab_type=top|bottom|double;
          NEVER give slabs axis, facing, hinge, or half.
        - stairs accept facing and half=top|bottom (NOT axis).
        - doors accept facing and hinge; door halves are placed automatically.
        - lantern accepts hanging=true|false, not facing or axis.
        - most ordinary blocks (glass, stone bricks, planks) have NO orientation.
        Do not add properties speculatively: omit them unless supported.
        door: TWO actual interactive door blocks (lower and upper halves), at (x,y,z),
            material *_door, facing north|south|east|west, optional hinge left|right.
            Door at y=1 when floor is at y=0; DO NOT fill the door opening with stone.
        bed: actual TWO-block bed, at (x,y,z), material *_bed, facing toward head.
        stairs_run: walkable diagonal stair run, h steps, w-wide, block *_stairs,
            with facing direction (upwards). Leave enough vertical headroom.
        table: tabletop of *_slab, size w*d; feet at y and tabletop at y+1, leg=oak_fence.
        chair: one chair-like *_stairs at (x,y,z), facing toward table.

        BLOCK ID ACCURACY IS CRITICAL: only use exact, existing Minecraft 1.21.1 IDs
        from this whitelist. Never invent wood variants such as light_oak_planks,
        pale_oak_planks, oak_wood_planks or birch_wood. The color adjective "light oak"
        is an aesthetic direction, NOT a valid block ID: use birch_planks for
        light wood, oak_planks for regular oak, dark_oak_planks for dark wood.
        Pale oak blocks are not available in Minecraft 1.21.1.
        For doors/stairs/slabs use a valid *_door, *_stairs, *_slab ID.
        Do not output any block name outside the allowed material palette.

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

        CONSTRUCTION COORDINATE CHECKLIST:
        - First choose one coherent origin, footprints and a single front direction.
        - Floor is a flat supported plane; standing/walking space starts above it.
        - Carve entrance from wall with AIR in openings stage, then put type=door
          at exactly the same x,z and lower Y; do not block the entrance.
        - Carve window holes at least 2 blocks above the floor before adding
          glass panes. Do not let later walls overwrite the openings.
        - Keep 3+ blocks of clear internal headroom, leave staircase path accessible.
        - Use 2-4 coordinated materials, a recognizable roof profile and few,
          intentional decorations. No floating torches, pots or lanterns.
        - Put potted flowers with type=block, block=potted_poppy on a supported table.
        - Do not invent unsupported operation names. e.g. use
          {"type":"block","block":"flower_pot","stage":"details","x":2,"y":1,"z":3}
          NOT {"type":"flower_pot",...}.
        - Check every number for collision, connection and reasonable scale.

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
        Every property name MUST have a colon; every pair of objects in an array
        MUST have a comma; use double quotes on keys and values; no unescaped
        newlines inside strings. Close all braces, brackets and quotes.
        Output a complete valid JSON object, without comments, trailing commas or markdown.
        """;

    private OpenAIPlanner() {}

    private static final String RETRY_INSTRUCTIONS = """
        Your previous response was malformed, truncated or invalid JSON. Generate a
        NEW complete building plan using the same requirements, NOT a fragment.
        IMPORTANT: valid strict JSON, double-quoted property names and values, colons
        between keys and values, commas between objects and properties, no trailing commas.
        Never place markdown fences around the response. Return at most 45 operations
        with compact one-line JSON. Every JSON object MUST be closed. Do not invent blocks.
        If the requested project is big, prioritize a small but finished, furnished
        building, with actual doors, walkable rooms and a roof over incomplete scenery.
        """;

    private static final class InvalidPlanJson extends Exception {
        InvalidPlanJson(String message) { super(message); }
    }

    public static CompletableFuture<String> plan(String prompt, BuilderConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (prompt == null || prompt.isBlank() || prompt.length() > PromptLimits.MAX_CHARS)
                    throw new IllegalArgumentException("Промпт должен быть длиной 1–" + PromptLimits.MAX_CHARS + " символов.");
                String key = BuilderConfig.key(config);
                try {
                    return requestPlan(prompt,config,key,false);
                } catch (InvalidPlanJson invalid) {
                    if (!config.retryMalformedJson)
                        throw new IllegalStateException(
                            "ИИ прислал повреждённый JSON. Включи retryMalformedJson в config.json или сократи промпт.");
                    AIBuilderMod.LOG.warn("AI Builder: malformed model plan; requesting one fresh shorter plan (extra API tokens may be charged).");
                    try {
                        return requestPlan(prompt,config,key,true);
                    } catch (InvalidPlanJson retryFailed) {
                        throw new IllegalStateException(
                            "ИИ дважды прислал некорректный JSON. Попробуй более короткий запрос или другую модель.");
                    }
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
            }
        }, IO);
    }

    private static String requestPlan(String prompt, BuilderConfig config, String key,
                                      boolean retry) throws Exception {
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", SYSTEM + (retry ? "\\n" + RETRY_INSTRUCTIONS : ""));
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(sys);
        messages.add(user);

        JsonObject request = new JsonObject();
        request.addProperty("model", config.model);
        request.add("messages", messages);

        // OpenAI GPT-4.1 supports JSON mode, including when accessed through Polza.
        // Other Polza model families may not, so do not force unsupported parameters.
        boolean jsonMode = "openai".equals(config.provider)
            || config.model.startsWith("openai/gpt-4.1")
            || config.model.startsWith("openai/gpt-4o")
            || config.model.startsWith("openai/gpt-5.4");
        if (jsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            request.add("response_format", format);
        }
        boolean newerGpt = config.model.startsWith("openai/gpt-5.")
            || config.model.startsWith("gpt-5.");
        if ("openai".equals(config.provider) || newerGpt)
            request.addProperty("max_completion_tokens", 9000);
        else
            request.addProperty("max_tokens", 9000);

        HttpResponse<String> response = send(config,key,request);
        // If a routed Polza model rejects JSON mode, fall back to plain chat.
        if (response.statusCode() == 400 && jsonMode && "polza".equals(config.provider)) {
            request.remove("response_format");
            response = send(config,key,request);
        }
        // Some proxy routing paths support only the older chat-completion token
        // parameter even when the upstream GPT family prefers max_completion_tokens.
        if (response.statusCode() == 400 && newerGpt && "polza".equals(config.provider)) {
            request.remove("max_completion_tokens");
            request.addProperty("max_tokens", 9000);
            request.remove("response_format");
            response = send(config,key,request);
        }

        if (response.statusCode() == 401)
            throw new IllegalStateException("Ключ " + BuilderConfig.providerName(config.provider) + " отклонён (401).");
        if (response.statusCode() == 402)
            throw new IllegalStateException("Не хватает средств на API (402).");
        if (response.statusCode() == 404)
            throw new IllegalStateException("Модель не найдена (404): " + config.model);
        if (response.statusCode() == 429)
            throw new IllegalStateException("Лимит запросов или баланс API (429).");
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("API вернул HTTP " + response.statusCode()
                + ". Проверь модель и доступ к сервису.");
        if (response.body().length() > 250_000)
            throw new InvalidPlanJson("Ответ API слишком большой.");

        JsonObject outer = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject choice = outer.getAsJsonArray("choices").get(0).getAsJsonObject();
        String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
            ? choice.get("finish_reason").getAsString() : "";
        if ("length".equals(finish))
            throw new InvalidPlanJson("ИИ не закончил JSON-план из-за ограничения длины.");
        JsonObject message = choice.getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull())
            throw new InvalidPlanJson("ИИ не вернул текст плана.");
        String result = message.get("content").getAsString();
        try {
            return PlanResponseParser.normalize(result);
        } catch (RuntimeException parse) {
            throw new InvalidPlanJson("ИИ вернул синтаксически некорректный JSON.");
        }
    }

    private static HttpResponse<String> send(BuilderConfig config,String key,JsonObject request)
            throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.endpoint()))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request),StandardCharsets.UTF_8))
            .build();
        return HTTP.send(httpRequest,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
