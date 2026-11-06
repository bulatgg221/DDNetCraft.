package dev.ddnetcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A non-destructive, client-only 2D tile arena. Rows are top-to-bottom in JSON. */
public final class DDNetMap {
    private static final Path MAP_DIR = FabricLoader.getInstance().getConfigDir().resolve("ddnetcraft/maps");
    public final String name;
    public final int width;
    public final int height;
    public final double spawnX;
    public final double spawnY;
    private final char[][] tiles; // bottom-to-top

    private DDNetMap(String name, int width, int height, double x, double y, char[][] tiles) {
        this.name = name; this.width = width; this.height = height;
        this.spawnX = x; this.spawnY = y; this.tiles = tiles;
    }

    public static DDNetMap load(String filename) throws Exception {
        if (!filename.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Map name: a-z, 0-9, underscore or dash only");
        }
        Files.createDirectories(MAP_DIR);
        Path target = MAP_DIR.resolve(filename + ".json");
        if (Files.notExists(target)) {
            if (!filename.equals("training")) throw new IllegalArgumentException("Map not found: " + target);
            try (InputStream source = DDNetMap.class.getResourceAsStream("/assets/ddnetcraft/maps/training.json")) {
                if (source == null) throw new IllegalStateException("Missing bundled training map");
                Files.copy(source, target);
            }
        }
        try (var reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String name = json.has("name") ? json.get("name").getAsString() : filename;
            JsonArray rows = json.getAsJsonArray("rows");
            if (rows == null || rows.isEmpty() || rows.size() > 64) {
                throw new IllegalArgumentException("Map must contain 1-64 rows");
            }
            int height = rows.size(), width = rows.get(0).getAsString().length();
            if (width < 4 || width > 100) throw new IllegalArgumentException("Map width must be 4-100");
            char[][] tiles = new char[height][width];
            for (int visualY = 0; visualY < height; visualY++) {
                String row = rows.get(visualY).getAsString();
                if (row.length() != width) throw new IllegalArgumentException("Unequal row length at " + visualY);
                for (int x = 0; x < width; x++) {
                    char c = row.charAt(x);
                    if (".#HUKFECL".indexOf(c) < 0) throw new IllegalArgumentException("Unsupported tile: " + c);
                    tiles[height - 1 - visualY][x] = c;
                }
            }
            JsonObject spawn = json.getAsJsonObject("spawn");
            double spawnX = spawn.get("x").getAsDouble();
            double spawnY = spawn.get("y").getAsDouble();
            if (!Double.isFinite(spawnX) || !Double.isFinite(spawnY) ||
                    spawnX < 0 || spawnX >= width || spawnY < 0 || spawnY >= height) {
                throw new IllegalArgumentException("Invalid spawn location");
            }
            return new DDNetMap(name, width, height, spawnX, spawnY, tiles);
        }
    }

    public char tile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return '#';
        return tiles[y][x];
    }

    public char tileAt(double x, double y) {
        return tile((int) Math.floor(x), (int) Math.floor(y));
    }

    public static boolean isSolid(char tile) {
        return tile == '#' || tile == 'H' || tile == 'U';
    }

    public boolean overlapsSolid(double centerX, double feetY) {
        int left = (int) Math.floor(centerX - 0.28);
        int right = (int) Math.floor(centerX + 0.28);
        int bottom = (int) Math.floor(feetY + 0.04);
        int top = (int) Math.floor(feetY + 1.73);
        for (int x = left; x <= right; x++)
            for (int y = bottom; y <= top; y++)
                if (isSolid(tile(x, y))) return true;
        return false;
    }
}
