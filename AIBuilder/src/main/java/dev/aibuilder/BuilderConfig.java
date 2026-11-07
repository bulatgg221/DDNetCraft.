package dev.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final Path OPENAI_KEY_FILE = DIRECTORY.resolve("api-key.txt");
    private static final Path POLZA_KEY_FILE = DIRECTORY.resolve("polza-key.txt");
    private static final Path CONFIG_FILE = DIRECTORY.resolve("config.json");

    public String provider = "polza";
    public String model = "openai/gpt-5.4-mini";
    /** One extra billable API request if a model returns invalid or truncated JSON. */
    public boolean retryMalformedJson = true;
    public int blocksPerTick = 300;
    public int maxBlocks = 30000;
    public int maxOperations = BuildLimits.DEFAULT_OPERATIONS;
    /** Marks configs migrated to the current operation limit. */
    public int operationLimitVersion = 1;
    public int maxHorizontalDistance = 48;
    public int maxVerticalDistance = 32;

    public static String defaultModel(String provider) {
        return "polza".equals(provider) ? "openai/gpt-5.4-mini" : "gpt-4.1-mini";
    }
    public static String providerName(String provider) {
        return "polza".equals(provider) ? "Polza AI" : "OpenAI";
    }
    public String endpoint() {
        return "polza".equals(provider)
            ? "https://polza.ai/api/v1/chat/completions"
            : "https://api.openai.com/v1/chat/completions";
    }
    private static Path keyFile(String provider) {
        return "polza".equals(provider) ? POLZA_KEY_FILE : OPENAI_KEY_FILE;
    }
    private static String validateProvider(String provider) {
        if (!"polza".equals(provider) && !"openai".equals(provider))
            throw new IllegalArgumentException("Провайдер должен быть polza или openai.");
        return provider;
    }
    private static String validateModel(String model) {
        if (model == null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]{1,99}"))
            throw new IllegalArgumentException("Некорректный идентификатор модели.");
        return model;
    }

    public static BuilderConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (Files.notExists(CONFIG_FILE)) {
                BuilderConfig defaults = new BuilderConfig();
                Files.writeString(CONFIG_FILE, GSON.toJson(defaults), StandardCharsets.UTF_8);
                return defaults;
            }
            String raw = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            JsonObject parsed = JsonParser.parseString(raw).getAsJsonObject();
            BuilderConfig config = GSON.fromJson(parsed, BuilderConfig.class);
            if (config == null) throw new IllegalArgumentException("Пустой config.json");
            // Keep the existing OpenAI setup intact when upgrading from version 0.2.x.
            if (!parsed.has("provider")) config.provider = "openai";
            // Gson initializes missing primitive boolean fields to false for old config files.
            if (!parsed.has("retryMalformedJson")) config.retryMalformedJson = true;
            if (!"polza".equals(config.provider) && !"openai".equals(config.provider))
                config.provider = "openai";
            if (config.model == null || !config.model.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]{1,99}"))
                config.model = defaultModel(config.provider);
            if ("polza".equals(config.provider) && !config.model.contains("/"))
                config.model = "openai/" + config.model;
            if ("openai".equals(config.provider) && config.model.startsWith("openai/"))
                config.model = config.model.substring("openai/".length());
            config.blocksPerTick = Math.clamp(config.blocksPerTick, 10, 1000);
            config.maxBlocks = Math.clamp(config.maxBlocks, 100, 50000);
            boolean legacyOperationLimit = !parsed.has("operationLimitVersion");
            int normalizedOperations = BuildLimits.configuredOperations(
                config.maxOperations, legacyOperationLimit);
            boolean migratedOperations = config.maxOperations != normalizedOperations
                || legacyOperationLimit;
            config.maxOperations = normalizedOperations;
            config.operationLimitVersion = 1;
            // Persist only a limit migration; no API key, model or provider is changed.
            if (migratedOperations)
                Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            config.maxHorizontalDistance = Math.clamp(config.maxHorizontalDistance, 16, 64);
            config.maxVerticalDistance = Math.clamp(config.maxVerticalDistance, 8, 48);
            return config;
        } catch (Exception e) {
            AIBuilderMod.LOG.warn("Cannot load config.json; using safe defaults: {}", e.toString());
            return new BuilderConfig();
        }
    }

    public static BuilderConfig saveProviderAndModel(String provider, String model) throws Exception {
        validateProvider(provider);
        validateModel(model);
        if ("polza".equals(provider) && !model.contains("/"))
            throw new IllegalArgumentException("Для Polza укажи ID вида openai/gpt-4.1-mini.");
        BuilderConfig config = load();
        config.provider = provider;
        config.model = model;
        Files.createDirectories(DIRECTORY);
        Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return config;
    }

    /** Secrets are stored per provider, never copied to a different API service. */
    public static void storeKey(String provider, String input) throws Exception {
        validateProvider(provider);
        String key = input == null ? "" : input.strip();
        boolean validPrefix = "polza".equals(provider)
            ? key.startsWith("pza_") || key.startsWith("sk-polza-")
            : key.startsWith("sk-");
        if (!validPrefix || key.length() < 20 || key.length() > 256 ||
            key.chars().anyMatch(c -> Character.isWhitespace(c) || c < 33 || c > 126))
            throw new IllegalArgumentException("Неверный формат ключа выбранного провайдера.");
        Files.createDirectories(DIRECTORY);
        Path file = keyFile(provider);
        Files.writeString(file, key + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // The host OS (such as Windows) applies its native file permissions.
        }
    }

    public static boolean hasStoredKey(String provider) {
        return Files.isRegularFile(keyFile(validateProvider(provider)));
    }
    public static void deleteStoredKey(String provider) throws java.io.IOException {
        Files.deleteIfExists(keyFile(validateProvider(provider)));
    }
    public static String key(BuilderConfig config) throws Exception {
        String environment = System.getenv("polza".equals(config.provider) ? "POLZA_API_KEY" : "OPENAI_API_KEY");
        if (environment != null && !environment.isBlank()) return environment.strip();
        Path file = keyFile(validateProvider(config.provider));
        if (!Files.isRegularFile(file)) throw new IllegalStateException(
            "Ключ для " + providerName(config.provider) + " не найден. Введи /ai key или нажми F8.");
        String key = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (key.isEmpty()) throw new IllegalStateException("Сохранённый API-ключ пуст.");
        return key;
    }
}
