package dev.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class BuilderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("aibuilder");
    public static final Path KEY_FILE = DIRECTORY.resolve("api-key.txt");
    private static final Path CONFIG_FILE = DIRECTORY.resolve("config.json");

    public String model = "gpt-4.1-mini";
    public int blocksPerTick = 300;
    public int maxBlocks = 30000;
    public int maxOperations = 100;
    public int maxHorizontalDistance = 48;
    public int maxVerticalDistance = 32;

    public static BuilderConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (!Files.isRegularFile(CONFIG_FILE)) {
                BuilderConfig config = new BuilderConfig();
                Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8);
                return config;
            }
            BuilderConfig config = GSON.fromJson(Files.readString(CONFIG_FILE), BuilderConfig.class);
            if (config == null) throw new IllegalArgumentException("empty configuration");
            config.model = config.model == null || !config.model.matches("[a-zA-Z0-9._-]{1,64}")
                ? "gpt-4.1-mini" : config.model;
            config.blocksPerTick = Math.clamp(config.blocksPerTick, 10, 1000);
            config.maxBlocks = Math.clamp(config.maxBlocks, 100, 50000);
            config.maxOperations = Math.clamp(config.maxOperations, 1, 120);
            config.maxHorizontalDistance = Math.clamp(config.maxHorizontalDistance, 16, 64);
            config.maxVerticalDistance = Math.clamp(config.maxVerticalDistance, 8, 48);
            return config;
        } catch (Exception exception) {
            AIBuilderMod.LOG.warn("Could not load config; defaults will be used: {}", exception.toString());
            return new BuilderConfig();
        }
    }

    /** The client GUI writes a private file; nothing secret is ever sent as a chat command. */
    public static void storeKey(String input) throws Exception {
        String key = input == null ? "" : input.strip();
        if (key.length() < 20 || key.length() > 256 || !key.startsWith("sk-")
            || key.chars().anyMatch(c -> Character.isWhitespace(c) || c < 33 || c > 126)) {
            throw new IllegalArgumentException("Invalid API key format.");
        }
        Files.createDirectories(DIRECTORY);
        Files.writeString(KEY_FILE, key + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(KEY_FILE, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows profile ACLs apply instead of POSIX modes.
        }
    }

    public static boolean hasStoredKey() {
        return Files.isRegularFile(KEY_FILE);
    }

    public static void deleteStoredKey() throws java.io.IOException {
        Files.deleteIfExists(KEY_FILE);
    }

    public static String key() throws Exception {
        String environment = System.getenv("OPENAI_API_KEY");
        if (environment != null && !environment.isBlank()) return environment.strip();
        if (!Files.isRegularFile(KEY_FILE)) throw new IllegalStateException(
            "Введи /aikey в Minecraft, вставь ключ в закрытое поле и сохрани. Не отправляй ключ в чат!");
        String key = Files.readString(KEY_FILE, StandardCharsets.UTF_8).strip();
        if (!key.startsWith("sk-")) throw new IllegalStateException("Файл api-key.txt не содержит ключ OpenAI API.");
        return key;
    }
}
