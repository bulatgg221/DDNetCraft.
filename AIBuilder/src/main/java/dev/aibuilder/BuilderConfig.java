package dev.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static String key() throws Exception {
        String environment = System.getenv("OPENAI_API_KEY");
        if (environment != null && !environment.isBlank()) return environment.strip();
        if (!Files.isRegularFile(KEY_FILE)) throw new IllegalStateException(
            "Создай файл config/aibuilder/api-key.txt с ключом OpenAI API (одной строкой). Не присылай ключ в чат!");
        String key = Files.readString(KEY_FILE, StandardCharsets.UTF_8).strip();
        if (!key.startsWith("sk-")) throw new IllegalStateException("Файл api-key.txt не содержит ключ OpenAI API.");
        return key;
    }
}
