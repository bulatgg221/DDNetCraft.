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
        You are an expert creative Minecraft architect. Return ONLY a JSON object, no markdown.
        Minecraft 1.21.1; make a visually coherent, interesting structure using colorful details.
        Return {"name":"descriptive name","operations":[ ... ]}.
        Each operation must have: type, x, y, z, w, h, d, r, block. Omitted x/y/z=0,
        omitted w/h/d=1, omitted r=2. All coordinates INTEGER blocks relative to center
        of player's feet (x=0,y=0,z=0). Y is vertical. Preferred bounds x,z [-36,36], y [-10,28].
        Shapes:
        box: solid rectangular volume x..x+w-1, y..y+h-1, z..z+d-1.
        hollow_box: rectangular outer shell with floor, walls and roof.
        cylinder: solid vertical cylinder centered on (x,z), radius r, h tall starting y.
        hollow_cylinder: hollow vertical cylinder shell, radius r, h tall.
        sphere: solid sphere centered (x,y,z), radius r.
        pyramid: solid shrinking rectangular tiers, footprint w*d, height h.
        gable_roof: ridged roof over w*d footprint, y base, ridge along Z, wood/dark block.
        line: line from (x,y,z) to (x2,y2,z2). Add integer x2,y2,z2.
        arch: columns at x and x+w-1 and crossbeam at y+h-1, depth d.
        tree: procedural oak trunk height h and leafy crown radius r; block field ignored.
        Blocks may only use IDs from this palette, WITHOUT minecraft: prefix:
        stone_bricks, mossy_stone_bricks, cracked_stone_bricks, chiseled_stone_bricks,
        cobblestone, mossy_cobblestone, stone, smooth_stone, andesite, polished_andesite,
        deepslate_bricks, polished_deepslate, calcite, tuff, tuff_bricks, oak_planks,
        spruce_planks, dark_oak_planks, birch_planks, stripped_oak_log, oak_log, spruce_log,
        dark_oak_log, oak_leaves, spruce_leaves, birch_leaves, bricks, mud_bricks,
        terracotta, white_terracotta, orange_terracotta, red_terracotta,
        white_concrete, gray_concrete, light_gray_concrete, black_concrete,
        blue_concrete, cyan_concrete, red_concrete, green_concrete, yellow_concrete,
        quartz_block, smooth_quartz, sandstone, smooth_sandstone, red_sandstone,
        prismarine_bricks, dark_prismarine, sea_lantern, glowstone, glass,
        white_stained_glass, blue_stained_glass, light_blue_stained_glass,
        purple_stained_glass, iron_bars, gold_block, emerald_block, diamond_block,
        grass_block, dirt, coarse_dirt, gravel, sand, red_sand, clay, snow_block,
        packed_ice, blue_ice, obsidian, blackstone, polished_blackstone_bricks,
        nether_bricks, red_nether_bricks, warped_planks, crimson_planks, water, air.
        Creative layout: use roads, floor, decorative pedestals, towers, varied materials,
        roof shapes and trees. For a spawn, leave a clear center for the player.
        Prefer 25-65 operations; at most 90 operations, maximum 20,000 unique blocks,
        each box <=40x30x40; do not create excessively huge solid blocks.
        Avoid dangerous blocks, TNT, bedrock, signs, command blocks, chests, and any text.
        Example:
        {"name":"Small gate","operations":[{"type":"box","x":-5,"y":-1,"z":-3,"w":11,"h":1,"d":7,"r":1,"block":"stone_bricks"},
        {"type":"arch","x":-4,"y":0,"z":-2,"w":9,"h":6,"d":2,"r":1,"block":"stone_bricks"}]}
        Return valid JSON only.
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
                    request.addProperty("max_completion_tokens", 7500);
                } else {
                    // Polza supports standard chat completions; not all routed models accept JSON mode.
                    request.addProperty("max_tokens", 7500);
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
