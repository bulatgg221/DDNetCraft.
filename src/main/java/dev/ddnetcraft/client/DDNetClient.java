package dev.ddnetcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Client-only DDNet 2D arcade mode. The virtual map is not written into the save;
 * this never registers entities, custom packets or dedicated-server state.
 * Vanilla movement is cancelled by ClientPlayerMovementMixin while enabled.
 */
public final class DDNetClient implements ClientModInitializer {
    public static DDNetClient INSTANCE;
    public static final double PIXELS_PER_BLOCK = 32.0;
    private static final TagKey<net.minecraft.block.Block> UNHOOKABLE =
        TagKey.of(RegistryKeys.BLOCK, Identifier.of("ddnet", "unhookable"));

    public enum Weapon { PISTOL, GRENADE, LASER }
    public static final class Projectile {
        public double x, y, vx, vy;
        public int ttl;
        public final Weapon weapon;
        public Projectile(double x, double y, double vx, double vy, int ttl, Weapon weapon) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.ttl = ttl; this.weapon = weapon;
        }
    }
    public record Beam(double x1, double y1, double x2, double y2, int age) {}
    public static final class Hook {
        public double x, y;
        public final double dx, dy;
        public boolean attached;
        public Hook(double x, double y, double dx, double dy) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy;
        }
    }

    private KeyBinding toggleKey, hookKey, fireKey, restartKey, pistolKey, grenadeKey, laserKey;
    private DDNetConfig config;
    private DDNetMap map;
    private boolean enabled, finished, grounded;
    private boolean jumpHeld;
    private int airJumpsLeft, frozenTicks, weaponCooldown, messageTicks, deaths;
    private int facing = 1;
    private long runTicks;
    private String notice = "";
    private Weapon selected = Weapon.PISTOL;
    private double anchorX, anchorY, anchorZ;
    private double x, y, vx, vy;
    private double savedX, savedY, savedZ;
    private double checkpointX, checkpointY;
    private Hook hook;
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public DDNetMap map() { return map; }
    public DDNetConfig config() { return config; }
    public Hook hook() { return hook; }
    public List<Projectile> projectiles() { return projectiles; }
    public List<Beam> beams() { return beams; }
    public double localX() { return x; }
    public double localY() { return y; }
    public double worldX(double local) { return anchorX + local; }
    public double worldY(double local) { return anchorY + local; }
    public double worldZ() { return anchorZ; }
    public Weapon weapon() { return selected; }
    public int frozenTicks() { return frozenTicks; }
    public int deaths() { return deaths; }
    public long runTicks() { return runTicks; }
    public boolean finished() { return finished; }
    public boolean grounded() { return grounded; }
    public String notice() { return notice; }
    public int messageTicks() { return messageTicks; }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config = DDNetConfig.load();
        String category = "key.category.ddnetcraft";
        toggleKey = key("key.ddnetcraft.toggle", GLFW.GLFW_KEY_G, category);
        hookKey = key("key.ddnetcraft.hook", GLFW.GLFW_KEY_Q, category);
        fireKey = key("key.ddnetcraft.fire", GLFW.GLFW_KEY_J, category);
        restartKey = key("key.ddnetcraft.restart", GLFW.GLFW_KEY_R, category);
        pistolKey = key("key.ddnetcraft.pistol", GLFW.GLFW_KEY_1, category);
        grenadeKey = key("key.ddnetcraft.grenade", GLFW.GLFW_KEY_2, category);
        laserKey = key("key.ddnetcraft.laser", GLFW.GLFW_KEY_3, category);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        WorldRenderEvents.AFTER_ENTITIES.register(DDNetRender::renderWorld);
        HudRenderCallback.EVENT.register((draw, counter) -> DDNetRender.renderHud(draw, MinecraftClient.getInstance()));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("ddnet")
                .executes(ctx -> { toggle(ctx.getSource().getClient()); return 1; })
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    enable(ctx.getSource().getClient()); return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    disable(ctx.getSource().getClient()); return 1;
                }))
                .then(ClientCommandManager.literal("restart").executes(ctx -> {
                    restart(ctx.getSource().getClient()); return 1;
                }))
                .then(ClientCommandManager.literal("reloadconfig").executes(ctx -> {
                    config = DDNetConfig.load();
                    say(ctx.getSource().getClient(), "Physics config reloaded.");
                    return 1;
                }))
                .then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            loadMap(ctx.getSource().getClient(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
            )
        );
    }

    private static KeyBinding key(String id, int key, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, category));
    }
    private void onEndTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !mc.isIntegratedServerRunning()) {
            // Do not restore position if the world is already unloaded.
            if (enabled) clear();
            return;
        }
        while (toggleKey.wasPressed()) toggle(mc);
        if (enabled) {
            while (restartKey.wasPressed()) restart(mc);
            while (pistolKey.wasPressed()) selected = Weapon.PISTOL;
            while (grenadeKey.wasPressed()) selected = Weapon.GRENADE;
            while (laserKey.wasPressed()) selected = Weapon.LASER;
        }
    }

    public boolean interceptMovement(MinecraftClient mc, ClientPlayerEntity player) {
        return enabled && map != null && mc.player == player && mc.isIntegratedServerRunning()
            && mc.currentScreen == null;
    }

    public void toggle(MinecraftClient mc) {
        if (enabled) disable(mc);
        else enable(mc);
    }
    public void enable(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !mc.isIntegratedServerRunning()) {
            say(mc, "DDNet is available in singleplayer only.");
            return;
        }
        if (enabled) return;
        try {
            map = DDNetMap.load("training");
        } catch (Exception ex) {
            say(mc, "Cannot load map: " + ex.getMessage());
            return;
        }
        savedX = mc.player.getX(); savedY = mc.player.getY(); savedZ = mc.player.getZ();
        // The map is projected around the player's original location.
        anchorX = savedX - map.spawnX;
        anchorY = savedY - map.spawnY;
        anchorZ = savedZ;
        enabled = true;
        restart(mc);
        say(mc, "DDNet mode ON. Q hook, J fire, 1-3 weapons, R restart, G toggle.");
    }
    public void disable(MinecraftClient mc) {
        if (!enabled) return;
        if (mc.player != null) {
            mc.player.setPosition(savedX, savedY, savedZ);
            mc.player.setVelocity(Vec3d.ZERO);
        }
        clear();
        say(mc, "DDNet mode OFF.");
    }
    private void clear() {
        enabled = false;
        map = null;
        projectiles.clear(); beams.clear(); hook = null;
        frozenTicks = 0; weaponCooldown = 0;
    }
    public void loadMap(MinecraftClient mc, String name) {
        if (mc.player == null || !mc.isIntegratedServerRunning()) {
            say(mc, "Create a singleplayer world first.");
            return;
        }
        DDNetMap loaded;
        try {
            loaded = DDNetMap.load(name);
        } catch (Exception ex) {
            say(mc, "Cannot load map: " + ex.getMessage());
            return;
        }
        if (!enabled) {
            savedX = mc.player.getX(); savedY = mc.player.getY(); savedZ = mc.player.getZ();
        }
        map = loaded;
        anchorX = savedX - map.spawnX;
        anchorY = savedY - map.spawnY;
        anchorZ = savedZ;
        enabled = true;
        restart(mc);
        say(mc, "Loaded " + loaded.name + " (client-only virtual map).");
    }

    public void restart(MinecraftClient mc) {
        if (!enabled || map == null) {
            say(mc, "Enable DDNet first: /ddnet on");
            return;
        }
        x = map.spawnX; y = map.spawnY;
        checkpointX = x; checkpointY = y;
        vx = 0; vy = 0;
        grounded = false; jumpHeld = false; airJumpsLeft = 1;
        frozenTicks = 0; weaponCooldown = 0;
        hook = null; projectiles.clear(); beams.clear();
        runTicks = 0; finished = false;
        if (mc.player != null) {
            mc.player.setPosition(worldX(x), worldY(y), worldZ());
            mc.player.setVelocity(Vec3d.ZERO);
        }
        announce("READY — reach the green FINISH tile.");
    }

    private void announce(String message) {
        notice = message; messageTicks = 100;
    }
    private static void say(MinecraftClient mc, String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§b[DDNetCraft] §f" + message), false);
    }
    public String timeLabel() {
        return String.format(Locale.ROOT, "%.2fs", runTicks / 20.0);
    }

    /** Executed instead of vanilla client tickMovement by a very narrow cancellable mixin. */
    public void movementTick(MinecraftClient mc) {
        if (!enabled || mc.player == null || map == null) return;
        ClientPlayerEntity player = mc.player;
        if (weaponCooldown > 0) weaponCooldown--;
        if (messageTicks > 0) messageTicks--;
        for (int i = beams.size() - 1; i >= 0; i--) {
            Beam b = beams.get(i);
            if (b.age <= 1) beams.remove(i);
            else beams.set(i, new Beam(b.x1(), b.y1(), b.x2(), b.y2(), b.age() - 1));
        }
        boolean hookHeld = hookKey.isPressed();
        if (!hookHeld) hook = null;
        else if (hook == null && !finished && frozenTicks == 0) fireHook(player);
        if (frozenTicks > 0) frozenTicks--;

        if (!finished) {
            runTicks++;
            grounded = map.overlapsSolid(x, y - 0.08);
            int dir = (mc.options.leftKey.isPressed() ? -1 : 0)
                + (mc.options.rightKey.isPressed() ? 1 : 0);
            if (dir != 0) facing = dir;
            double limit = (grounded ? config.ground_control_speed : config.air_control_speed) / PIXELS_PER_BLOCK;
            double acceleration = (grounded ? config.ground_control_accel : config.ground_control_accel * 0.45) / PIXELS_PER_BLOCK;
            if (frozenTicks > 0) { vx = 0; vy = 0; hook = null; }
            else {
                if (dir != 0) vx = approach(vx, dir * limit, acceleration);
                else vx *= grounded ? 0.65 : config.air_friction;
                if (Math.abs(vx) < 0.001) vx = 0;
                boolean jumping = mc.options.jumpKey.isPressed();
                if (jumping && !jumpHeld) {
                    if (grounded) {
                        vy = config.ground_jump_impulse / PIXELS_PER_BLOCK;
                        grounded = false;
                        airJumpsLeft = 1;
                    } else if (airJumpsLeft > 0) {
                        vy = config.air_jump_impulse / PIXELS_PER_BLOCK;
                        airJumpsLeft--;
                    }
                }
                jumpHeld = jumping;
                double gravityMultiplier = map.tileAt(x, y + 0.5) == 'L' ? 0.35 : 1.0;
                vy = Math.max(-0.6, vy - config.gravity * gravityMultiplier / PIXELS_PER_BLOCK);
                updateHook(mc);
                if (fireKey.isPressed() && weaponCooldown == 0) shoot(player);
            }
            moveWithMapCollision();
            updateProjectiles();
            checkHazards();
        }
        player.setPosition(worldX(x), worldY(y), worldZ());
        player.setVelocity(vx, vy, 0);
        player.fallDistance = 0;
    }

    private static double approach(double current, double target, double amount) {
        if (current < target) return Math.min(current + amount, target);
        return Math.max(current - amount, target);
    }
    private void moveWithMapCollision() {
        // Fixed substeps avoid tunneling through the logical 1-block tiles.
        double stepX = vx / Math.max(1, (int) Math.ceil(Math.abs(vx) / 0.1));
        int stepsX = Math.max(1, (int) Math.ceil(Math.abs(vx) / 0.1));
        for (int i = 0; i < stepsX; i++) {
            if (map.overlapsSolid(x + stepX, y)) { vx = 0; break; }
            x += stepX;
        }
        int stepsY = Math.max(1, (int) Math.ceil(Math.abs(vy) / 0.1));
        double stepY = vy / stepsY;
        for (int i = 0; i < stepsY; i++) {
            if (map.overlapsSolid(x, y + stepY)) {
                if (vy < 0) { grounded = true; airJumpsLeft = 1; }
                vy = 0; break;
            }
            y += stepY;
        }
    }

    private void checkHazards() {
        char foot = map.tileAt(x, y + 0.15);
        char upper = map.tileAt(x, y + 1.0);
        if (foot == 'K' || upper == 'K') {
            deaths++;
            respawn();
            announce("DEATH — restart with R.");
        } else if (foot == 'F' || upper == 'F') {
            frozenTicks = Math.max(frozenTicks, 36);
        } else if (foot == 'C') {
            checkpointX = x; checkpointY = y;
        } else if (foot == 'E' || upper == 'E') {
            finished = true; hook = null;
            announce("FINISHED! " + timeLabel());
        }
    }
    private void respawn() {
        x = checkpointX; y = checkpointY; vx = 0; vy = 0;
        hook = null; projectiles.clear(); beams.clear();
        frozenTicks = 0; grounded = false; airJumpsLeft = 1;
        runTicks = 0;
    }

    private double[] aim(ClientPlayerEntity player) {
        Vec3d look = player.getRotationVec(1);
        double dx = Math.abs(look.x) < 0.1 ? facing : look.x;
        double dy = look.y;
        double mag = Math.hypot(dx, dy);
        return new double[]{ dx / Math.max(mag, 0.001), dy / Math.max(mag, 0.001) };
    }
    private void fireHook(ClientPlayerEntity player) {
        double[] dir = aim(player);
        hook = new Hook(x, y + 1.0, dir[0], dir[1]);
    }
    private void updateHook(MinecraftClient mc) {
        if (hook == null || !hookKey.isPressed()) return;
        double maxLength = config.hook_length / PIXELS_PER_BLOCK;
        if (!hook.attached) {
            int n = Math.max(1, (int)Math.ceil(config.hook_fire_speed / PIXELS_PER_BLOCK / 0.15));
            double distanceStep = config.hook_fire_speed / PIXELS_PER_BLOCK / n;
            for (int i = 0; i < n; i++) {
                hook.x += hook.dx * distanceStep;
                hook.y += hook.dy * distanceStep;
                if (Math.hypot(hook.x - x, hook.y - (y + 1.0)) > maxLength) {
                    hook = null; return;
                }
                char tile = map.tileAt(hook.x, hook.y);
                if (tile == 'U') { hook = null; return; }
                if (DDNetMap.isSolid(tile)) { hook.attached = true; break; }
                BlockPos blockPos = BlockPos.ofFloored(worldX(hook.x), worldY(hook.y), worldZ());
                BlockState state = mc.world.getBlockState(blockPos);
                if (!state.isAir() && !state.getCollisionShape(mc.world, blockPos).isEmpty()) {
                    if (state.isIn(UNHOOKABLE)) { hook = null; return; }
                    hook.attached = true; break;
                }
            }
        }
        if (hook != null && hook.attached) {
            double dx = hook.x - x;
            double dy = hook.y - (y + 0.8);
            double distance = Math.hypot(dx, dy);
            if (distance > maxLength + 1.0) { hook = null; return; }
            if (distance > 0.5) {
                double a = config.hook_drag_accel / PIXELS_PER_BLOCK;
                vx += dx / distance * a; vy += dy / distance * a;
                double speed = Math.hypot(vx, vy);
                if (speed > 0.95) { vx *= 0.95 / speed; vy *= 0.95 / speed; }
            }
        }
    }

    private void shoot(ClientPlayerEntity player) {
        double[] dir = aim(player);
        double mx = x + dir[0] * 0.6, my = y + 1.0 + dir[1] * 0.6;
        if (selected == Weapon.PISTOL) {
            projectiles.add(new Projectile(mx, my, dir[0] * 0.75, dir[1] * 0.75, 40, Weapon.PISTOL));
            vx -= dir[0] * 0.065; vy -= dir[1] * 0.065;
            weaponCooldown = 8;
        } else if (selected == Weapon.GRENADE) {
            projectiles.add(new Projectile(mx, my, dir[0] * 0.36, dir[1] * 0.36, 65, Weapon.GRENADE));
            weaponCooldown = 25;
        } else {
            fireLaser(mx, my, dir[0], dir[1]);
            weaponCooldown = 18;
        }
    }
    private void updateProjectiles() {
        Iterator<Projectile> it = projectiles.iterator();
        List<Projectile> explode = new ArrayList<>();
        while (it.hasNext()) {
            Projectile p = it.next();
            if (p.weapon == Weapon.GRENADE) p.vy -= 0.018;
            double targetX = p.x + p.vx, targetY = p.y + p.vy;
            int substeps = Math.max(1, (int)Math.ceil(Math.hypot(p.vx, p.vy) / 0.12));
            double sx = p.vx / substeps, sy = p.vy / substeps;
            boolean hit = false;
            for (int i = 0; i < substeps; i++) {
                p.x += sx; p.y += sy;
                if (DDNetMap.isSolid(map.tileAt(p.x, p.y))) { hit = true; break; }
            }
            if (--p.ttl <= 0 || hit) {
                if (p.weapon == Weapon.GRENADE) explode.add(p);
                it.remove();
            }
        }
        for (Projectile p : explode) {
            double dx = x - p.x, dy = y + 0.8 - p.y, r = Math.hypot(dx, dy);
            if (r < 4.5) {
                double force = (4.5 - Math.max(0.25, r)) * 0.13;
                vx += dx / Math.max(0.25, r) * force;
                vy += dy / Math.max(0.25, r) * force;
            }
            // Explosion visual: short-lived horizontal and vertical beams.
            beams.add(new Beam(p.x - 0.6, p.y, p.x + 0.6, p.y, 8));
            beams.add(new Beam(p.x, p.y - 0.6, p.x, p.y + 0.6, 8));
        }
    }
    private void fireLaser(double startX, double startY, double dx, double dy) {
        double cx = startX, cy = startY;
        for (int bounce = 0; bounce <= 2; bounce++) {
            double lastX = cx, lastY = cy;
            boolean hit = false;
            for (int step = 0; step < 160; step++) {
                double nx = cx + dx * 0.15, ny = cy + dy * 0.15;
                if (DDNetMap.isSolid(map.tileAt(nx, ny))) {
                    beams.add(new Beam(lastX, lastY, cx, cy, 12));
                    // Reflect from the nearest tile boundary.
                    if (DDNetMap.isSolid(map.tileAt(nx, cy))) dx = -dx;
                    else dy = -dy;
                    cx += dx * 0.03; cy += dy * 0.03;
                    hit = true;
                    break;
                }
                cx = nx; cy = ny;
            }
            if (!hit) { beams.add(new Beam(lastX, lastY, cx, cy, 12)); break; }
        }
        double ax = cx - x, ay = cy - (y + 0.8);
        double d = Math.hypot(ax, ay);
        if (d > 0.1) { vx += ax / d * 0.15; vy += ay / d * 0.15; }
    }
}
