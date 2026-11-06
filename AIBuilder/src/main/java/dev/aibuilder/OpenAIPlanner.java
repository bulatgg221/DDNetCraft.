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
        You are an expert ORIGINAL Minecraft 1.21.1 architectural generator.
        The player's text is the design brief. Invent a NEW coherent block layout for
        THIS request. The output must be a unique constructed architectural design,
        not a demonstration building with changed dimensions, colors or materials.
        You must output exact relative coordinates and materials for every component.
        The game DOES NOT design buildings for you; it only executes your instructions.

        CRITICAL: Never output type=house/cottage/manor/villa/farmhouse/chalet/longhouse,
        castle/fortress/citadel. These are FIXED prebuilt demos and are forbidden for
        AI-generated requests. Use only actual low-level placement primitives listed
        below. If the user asks for a castle, invent its towers, gatehouse, keep, wall
        outline, courtyard and staircases YOURSELF with meaningful relative positions.
        If the user asks for a house, invent its footprint, wings, roof, rooms and
        facade YOURSELF. NEVER just output a one-operation template.

        Return exactly ONE strict JSON object, no markdown or explanations:
        {"name":"Original project name","operations":[{"type":"box","stage":"foundation",
        "block":"stone_bricks","x":6,"y":0,"z":6,"w":15,"h":1,"d":13}, ...]}
        Each operation must have type, stage, block, x,y,z. Supply dimensions w,h,d
        only when a shape uses them; supply r only for radius-based primitives.
        Coordinates are in blocks relative to the player. Y increases upward.
        Build region: x,z between -40 and +40, y between -10 and +25.
        Keep the player's own spawn space around (0,0,0) clear.
        Each requested build should start near but NOT on the player, usually
        around x=6,z=6; choose dimensions based on the actual user brief.
        Max 90 operations, target 24-65 meaningful ones for large projects.
        Max 25,000 placed blocks in all operations. Avoid wasteful filled volumes.

        PLAN YOUR DESIGN INTERNALLY BEFORE WRITING JSON:
        1) Choose a distinctive silhouette and an accurate footprint using the
           user's specified style, scale, asymmetry, number of towers/wings/rooms.
        2) Establish named regions mentally: outer walls, entrance, interior,
           towers, secondary wings, roof, garden and circulation paths. All of
           their bounding boxes must fit with no accidental intersections.
        3) Compute the actual x/y/z for each component from a COMMON footprint,
           not random scattered numbers. Place structural blocks first, then
           carve openings, glazing, doors and furnishing. No floating parts.
        4) Check entrance lies on a wall and doors have TWO clear vertical cells.
           Windows must be actual wall holes filled with glass panes at the SAME
           x/z coordinates. Walkable rooms have at least 3 blocks headroom.
        5) Check the stair endpoint reaches the second floor and there is an air
           stairwell hole and landing. Roof must touch and cover its walls.
        6) Choose at least two contrasting blocks and intentional decorative
           details, but structural correctness is more important than ornaments.
        7) For a castle use separate towers, crenellations, gateway, courtyard,
           keep and connecting walls. Never make the courtyard a filled cube.
           Give it a unique castle outline based on the user's request.
        8) For modern villas choose clean flat roofs, bright walls and larger
           glass panels. For Japanese choose overhangs and darker hip roofs.
           For medieval choose timber frames, stone foundations and pitched roofs.

        AVAILABLE PRIMITIVES (the Minecraft mod executes exactly these):
        box: solid rectangular fill, x,y,z MIN corner, dimensions w X, h Y, d Z.
        hollow_box: 1-block shell of rectangle (includes floor and roof).
        cylinder: solid vertical cylinder CENTERED x,z, with radius r and height h.
        hollow_cylinder: hollow vertical cylinder CENTERED x,z, radius r, height h.
        sphere: filled sphere centered x,y,z, radius r.
        pyramid: shrinking rectangular mass with footprint w*d and height h.
        roof_stairs: 2-sided actual stair roof, footprint w X by d Z,
          base y on both X edges; center rises by min(dx,w-1-dx).
        hip_roof: 4-sided stair roof over w X and d Z, converging to center ridge.
        gable_fill: triangular wall under pitched roof, width w along X, depth d<=3.
        gable_roof: sloping roof made from full blocks, footprint w*d.
        arch: two pillars and top crossbeam, w>=3,h>=3,depth d.
        line: x,y,z to x2,y2,z2, straight line of material.
        block: place precisely ONE material, with optional state hints.
        door: place REAL two-block door with facing north/south/east/west,
          hinge left/right. Its base y must be ONE ABOVE the walking floor.
        bed: place real two-block bed facing its head direction.
        stairs_run: climb from x,y,z in facing direction, h steps, width w.
          Do not block the overhead path with slabs/floors/ceilings.
        table: w*d tabletop one block above supporting fence legs at x,y,z.
        chair: oriented stair chair at x,y,z with facing north/south/east/west.
        tree: log trunk of height h and leaves canopy radius r at x,y,z.

        VALID STAGES (these determine overlap priority independently of JSON order):
        foundation (0), walls (1), roof (2), openings (3), details (4),
        interior (5), landscape (6).
        A higher stage replaces blocks from earlier stages at the same position.
        Doors, beds and their reserved space should not be covered.
        Use "openings" + "block":"air" to carve windows/entrances in walls,
        THEN "details" + door or glass_pane at matching coordinates.
        DO NOT use "hollow_box" as a giant filled room.
        For intricate stone castle walls, use slender box ops (h/d = 1) and
        cylinder towers with clear interiors. Towers need intentional access.

        ALLOWED BLOCK STATE HINTS:
        facing for oriented stairs, traps, doors/beds; axis for logs; slab_type
        for slabs; hanging for lanterns; hinge for doors; half for stairs.
        Do not set axis on slabs or planks. Use type=door for true doors,
        NOT type=block. Use type=block and block=lantern for lanterns.
        Do not invent operation names like window, room, staircase, castle, house
        or arbitrary material names. Only types listed above.
        Coordinates and all dimensions must be integer numbers, w/h/d/r positive.
        Never output secrets, Minecraft commands, entities or block inventories.

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


        A VALID DOOR AND WINDOW COORDINATION EXAMPLE (NOT a ready-made building):
        {"type":"box","stage":"openings","block":"air",
         "x":12,"y":1,"z":6,"w":1,"h":2,"d":1}
        {"type":"door","stage":"details","block":"spruce_door",
         "x":12,"y":1,"z":6,"facing":"north"}
        {"type":"box","stage":"openings","block":"air",
         "x":17,"y":3,"z":6,"w":2,"h":2,"d":1}
        {"type":"box","stage":"details","block":"glass_pane",
         "x":17,"y":3,"z":6,"w":2,"h":2,"d":1}

        These four operations only illustrate coordinates, not any building plan.
        Invent the rest of the geometry completely from the user brief.
        Output strict well-formed JSON with all braces and brackets closed.
        Avoid trailing commas, markdown fences and comments.
        """;

    private OpenAIPlanner() {}

    private static final String RETRY_INSTRUCTIONS = """
        Your previous response was malformed, truncated or invalid JSON. Generate a
        NEW complete building plan using the same requirements, NOT a fragment.
        IMPORTANT: valid strict JSON, double-quoted property names and values, colons
        between keys and values, commas between objects and properties, no trailing commas.
        Never place markdown fences around the response. Return at most 55 operations
        with compact JSON. Every JSON object MUST be closed. Do not invent blocks.
        NEVER use house, castle, fortress or other premade macros.
        Every building must contain individual original structural operations:
        walls, towers, carved windows, actual doors, roofing, and interiors.
        If the requested project is big, prioritize a smaller COMPLETE original
        layout over an unfinished large shell.
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
                            "ИИ прислал неподходящий план. Включи retryMalformedJson в config.json или уточни запрос.");
                    AIBuilderMod.LOG.warn("AI Builder: malformed model plan; requesting one fresh shorter plan (extra API tokens may be charged).");
                    try {
                        return requestPlan(prompt,config,key,true);
                    } catch (InvalidPlanJson retryFailed) {
                        throw new IllegalStateException(
                            "ИИ дважды не смог составить оригинальный план. Попробуй упростить постройку или другую модель.");
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
            String normalized = PlanResponseParser.normalize(result);
            return GeneratedPlanGuard.requireOriginalGeometry(normalized);
        } catch (RuntimeException parse) {
            throw new InvalidPlanJson("ИИ прислал повреждённый JSON или готовый шаблон вместо новых координат.");
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
