package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict interpreter; text returned by an LLM is never executed as Minecraft commands or Java code. */
public final class PlanCompiler {
    private PlanCompiler() {}

    public static final Set<String> MATERIALS = Set.of(
        "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
        "cobblestone", "mossy_cobblestone", "stone", "smooth_stone", "andesite", "polished_andesite",
        "deepslate_bricks", "polished_deepslate", "calcite", "tuff", "tuff_bricks",
        "oak_planks", "spruce_planks", "dark_oak_planks", "birch_planks", "stripped_oak_log",
        "oak_log", "spruce_log", "dark_oak_log", "oak_leaves", "spruce_leaves", "birch_leaves",
        "bricks", "mud_bricks", "terracotta", "white_terracotta", "orange_terracotta",
        "red_terracotta", "white_concrete", "gray_concrete", "light_gray_concrete", "black_concrete",
        "blue_concrete", "cyan_concrete", "red_concrete", "green_concrete", "yellow_concrete",
        "quartz_block", "smooth_quartz", "sandstone", "smooth_sandstone", "red_sandstone",
        "prismarine_bricks", "dark_prismarine", "sea_lantern", "glowstone",
        "glass", "white_stained_glass", "blue_stained_glass", "light_blue_stained_glass",
        "purple_stained_glass", "iron_bars", "gold_block", "emerald_block", "diamond_block",
        "grass_block", "dirt", "coarse_dirt", "gravel", "sand", "red_sand", "clay", "snow_block",
        "packed_ice", "blue_ice", "obsidian", "blackstone", "polished_blackstone_bricks",
        "nether_bricks", "red_nether_bricks", "warped_planks", "crimson_planks", "water", "air"
    );

    public record Placement(BlockPos pos, BlockState state) {}
    public record CompiledPlan(String name, List<Placement> blocks, int operations) {}

    public static CompiledPlan compile(String json, BlockPos origin, BuilderConfig cfg) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) throw new IllegalArgumentException("План должен быть JSON-объектом.");
        JsonObject root = rootElement.getAsJsonObject();
        String name = root.has("name") ? root.get("name").getAsString() : "AI Structure";
        if (name.length() > 80) name = name.substring(0, 80);
        JsonArray operations = root.getAsJsonArray("operations");
        if (operations == null || operations.isEmpty() || operations.size() > cfg.maxOperations)
            throw new IllegalArgumentException("Ожидается 1–" + cfg.maxOperations + " операций.");

        LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (JsonElement element : operations) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Операция не является объектом.");
            JsonObject op = element.getAsJsonObject();
            String type = string(op, "type", "box");
            int x = number(op, "x", 0), y = number(op, "y", 0), z = number(op, "z", 0);
            int w = number(op, "w", 1), h = number(op, "h", 1), d = number(op, "d", 1);
            int r = number(op, "r", 2);
            if (w < 1 || w > 80 || d < 1 || d > 80 || h < 1 || h > 48 || r < 1 || r > 20)
                throw new IllegalArgumentException("Недопустимый размер геометрической операции.");
            if (Math.abs(x) > 96 || Math.abs(z) > 96 || Math.abs(y) > 80)
                throw new IllegalArgumentException("Слишком большое смещение операции.");
            String material = string(op, "block", "stone_bricks");
            BlockState block = block(material);
            switch (type) {
                case "box", "hollow_box" -> {
                    for (int dx = 0; dx < w; dx++) for (int dy = 0; dy < h; dy++) for (int dz = 0; dz < d; dz++) {
                        if (type.equals("box") || dx == 0 || dx == w-1 || dy == 0 || dy == h-1 || dz == 0 || dz == d-1)
                            add(blocks, origin, cfg, x+dx, y+dy, z+dz, block);
                    }
                }
                case "cylinder", "hollow_cylinder" -> {
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                        int distance = dx*dx + dz*dz;
                        if (distance > r*r || (type.equals("hollow_cylinder") && distance < (r-1)*(r-1))) continue;
                        for (int dy = 0; dy < h; dy++) add(blocks,origin,cfg,x+dx,y+dy,z+dz,block);
                    }
                }
                case "sphere" -> {
                    for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                        if (dx*dx + dy*dy + dz*dz <= r*r) add(blocks,origin,cfg,x+dx,y+dy,z+dz,block);
                    }
                }
                case "pyramid" -> {
                    for (int dy=0; dy<h; dy++) {
                        int startX = Math.min(dy, (w-1)/2), startZ = Math.min(dy, (d-1)/2);
                        for (int dx=startX; dx<w-startX; dx++) for (int dz=startZ; dz<d-startZ; dz++)
                            add(blocks,origin,cfg,x+dx,y+dy,z+dz,block);
                    }
                }
                case "gable_roof" -> {
                    for (int dx=0; dx<w; dx++) for (int dz=0; dz<d; dz++) {
                        int rise = Math.min(dx,w-1-dx);
                        add(blocks,origin,cfg,x+dx,y+rise,z+dz,block);
                    }
                }
                case "line" -> {
                    int x2 = number(op,"x2",x), y2 = number(op,"y2",y), z2 = number(op,"z2",z);
                    int steps = Math.max(1, Math.max(Math.abs(x2-x),Math.max(Math.abs(y2-y),Math.abs(z2-z))));
                    if (steps > 120) throw new IllegalArgumentException("Линия слишком длинная.");
                    for (int i=0; i<=steps; i++) {
                        add(blocks,origin,cfg,(int)Math.round(x+(x2-x)*(i/(double)steps)),
                            (int)Math.round(y+(y2-y)*(i/(double)steps)),
                            (int)Math.round(z+(z2-z)*(i/(double)steps)),block);
                    }
                }
                case "arch" -> {
                    if (w < 3 || h < 3) throw new IllegalArgumentException("Арка: w и h должны быть >=3.");
                    for (int dy=0; dy<h; dy++) for (int dz=0; dz<d; dz++) {
                        add(blocks,origin,cfg,x,y+dy,z+dz,block);
                        add(blocks,origin,cfg,x+w-1,y+dy,z+dz,block);
                    }
                    for (int dx=0; dx<w; dx++) for (int dz=0; dz<d; dz++)
                        add(blocks,origin,cfg,x+dx,y+h-1,z+dz,block);
                }
                case "tree" -> {
                    if (h < 3 || h > 14 || r > 5) throw new IllegalArgumentException("Дерево: h=3–14, r=1–5.");
                    BlockState leaves = block("oak_leaves");
                    for (int dx=-r; dx<=r; dx++) for (int dy=-r; dy<=r; dy++) for (int dz=-r; dz<=r; dz++) {
                        if (dx*dx+dy*dy+dz*dz <= r*r && dy >= -r+1)
                            add(blocks,origin,cfg,x+dx,y+h-1+dy,z+dz,leaves);
                    }
                    for (int dy=0;dy<h;dy++) add(blocks,origin,cfg,x,y+dy,z,block("oak_log"));
                }
                default -> throw new IllegalArgumentException("Неизвестная форма: " + type);
            }
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("Получился пустой проект.");
        List<Placement> output = new ArrayList<>(blocks.size());
        blocks.forEach((pos, state) -> output.add(new Placement(pos,state)));
        return new CompiledPlan(name, output, operations.size());
    }

    private static BlockState block(String name) {
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        if (!MATERIALS.contains(name)) throw new IllegalArgumentException("Недопустимый блок: " + name);
        BlockState state = Registries.BLOCK.get(Identifier.of("minecraft", name)).getDefaultState();
        if (state.getBlock() instanceof LeavesBlock) state = state.with(LeavesBlock.PERSISTENT, true);
        return state;
    }
    private static String string(JsonObject object,String key,String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
    private static int number(JsonObject object,String key,int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }
    private static void add(Map<BlockPos,BlockState> out,BlockPos anchor,BuilderConfig cfg,
                            int x,int y,int z,BlockState state) {
        if (Math.abs(x)>cfg.maxHorizontalDistance || Math.abs(z)>cfg.maxHorizontalDistance ||
            Math.abs(y)>cfg.maxVerticalDistance)
            throw new IllegalArgumentException("Постройка вышла за безопасную границу. Уменьши размер.");
        BlockPos pos = anchor.add(x,y,z);
        if (pos.getY() < -64 || pos.getY() > 319) throw new IllegalArgumentException("Высота мира вне диапазона.");
        out.put(pos,state);
        if (out.size() > cfg.maxBlocks) throw new IllegalArgumentException(
            "Слишком много блоков (максимум " + cfg.maxBlocks + "). Уменьши проект.");
    }
}
