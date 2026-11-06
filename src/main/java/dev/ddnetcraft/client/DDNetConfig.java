package dev.ddnetcraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** DDNet physics units are pixels per tick; 32 pixels = one Minecraft block. */
public final class DDNetConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ddnetcraft/physics.json");

    public double gravity = 0.5;
    public double ground_control_speed = 10.0;
    public double ground_control_accel = 2.0;
    public double air_control_speed = 5.0;
    public double air_friction = 0.95;
    public double ground_jump_impulse = 13.2;
    public double air_jump_impulse = 12.0;
    public double hook_length = 380.0;
    public double hook_fire_speed = 80.0;
    public double hook_drag_accel = 3.0;

    private static double bounded(double number, double def, double min, double max) {
        return Double.isFinite(number) ? Math.max(min, Math.min(max, number)) : def;
    }

    public void validate() {
        gravity = bounded(gravity, 0.5, 0, 3);
        ground_control_speed = bounded(ground_control_speed, 10, 1, 45);
        ground_control_accel = bounded(ground_control_accel, 2, 0.01, 20);
        air_control_speed = bounded(air_control_speed, 5, 1, 45);
        air_friction = bounded(air_friction, 0.95, 0, 1);
        ground_jump_impulse = bounded(ground_jump_impulse, 13.2, 1, 45);
        air_jump_impulse = bounded(air_jump_impulse, 12, 1, 45);
        hook_length = bounded(hook_length, 380, 20, 2000);
        hook_fire_speed = bounded(hook_fire_speed, 80, 5, 2000);
        hook_drag_accel = bounded(hook_drag_accel, 3, 0.01, 40);
    }

    public static DDNetConfig load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                DDNetConfig defaults = new DDNetConfig();
                Files.writeString(FILE, GSON.toJson(defaults), StandardCharsets.UTF_8);
                return defaults;
            }
            DDNetConfig config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), DDNetConfig.class);
            if (config == null) throw new IllegalArgumentException("Empty physics config");
            config.validate();
            return config;
        } catch (Exception ex) {
            System.err.println("[DDNetCraft] Cannot read physics config: " + ex);
            return new DDNetConfig();
        }
    }
}
