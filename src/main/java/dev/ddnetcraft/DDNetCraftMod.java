package dev.ddnetcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** DDNet-inspired mechanics, implemented independently for Fabric. */
public final class DDNetCraftMod implements ModInitializer {
    public static final String MOD_ID = "ddnetcraft";
    private static final double MAX_HOOK_RANGE = 26.0;
    private static long tick;

    private record Hook(Vec3d anchor, UUID target, RegistryKey<World> dimension, long expires) {}
    private record Checkpoint(RegistryKey<World> dimension, Vec3d position, float yaw, float pitch) {}

    private static final Map<UUID, Hook> HOOKS = new HashMap<>();
    private static final Map<UUID, Long> FROZEN = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    private static final Map<UUID, Checkpoint> CHECKPOINTS = new HashMap<>();
    private static final Map<UUID, Long> RACES = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_FLOOR = new HashMap<>();

    public static final Item HOOK = Registry.register(
        Registries.ITEM, Identifier.of(MOD_ID, "hook"),
        new HookItem(new Item.Settings().maxCount(1))
    );
    public static final Item UNFREEZER = Registry.register(
        Registries.ITEM, Identifier.of(MOD_ID, "unfreezer"),
        new Item(new Item.Settings().maxCount(1))
    );

    @Override public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(HOOK);
            entries.add(UNFREEZER);
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND || !(entity instanceof PlayerEntity other)) return ActionResult.PASS;
            Item selected = player.getStackInHand(hand).getItem();
            if (selected != HOOK && selected != UNFREEZER) return ActionResult.PASS;
            if (!world.isClient() && player instanceof ServerPlayerEntity actor && other instanceof ServerPlayerEntity friend) {
                if (selected == UNFREEZER) {
                    if (FROZEN.containsKey(friend.getUuid())) {
                        unfreeze(friend);
                        notify(actor, "Товарищ разморожен!");
                    } else notify(actor, "Этот игрок не заморожен.");
                } else togglePlayerHook(actor, friend);
            }
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(DDNetCraftMod::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanup(handler.player.getUuid()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ddnet")
            .then(CommandManager.literal("kit").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                give(player, new ItemStack(HOOK));
                give(player, new ItemStack(UNFREEZER));
                notify(player, "Набор выдан. Хук: ПКМ по блоку или игроку.");
                return 1;
            }))
            .then(CommandManager.literal("reset").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                Checkpoint cp = CHECKPOINTS.get(player.getUuid());
                if (cp == null) {
                    notify(player, "Чекпоинт ещё не установлен. Найди лаймовый блок старта.");
                    return 0;
                }
                ServerWorld target = player.getServerWorld().getServer().getWorld(cp.dimension());
                if (target == null) return 0;
                HOOKS.remove(player.getUuid());
                unfreeze(player);
                player.teleport(target, cp.position().x, cp.position().y, cp.position().z, cp.yaw(), cp.pitch());
                player.setVelocity(Vec3d.ZERO);
                notify(player, "Возврат на чекпоинт.");
                return 1;
            }))
            .then(CommandManager.literal("status").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                Long start = RACES.get(player.getUuid());
                notify(player, start == null ? "Забег не начат." : "Время: " + formatTicks(tick - start));
                return 1;
            }))
            .then(CommandManager.literal("help").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                notify(player, "/ddnet kit | reset | status | help");
                notify(player, "Лаймовый бетон = старт; золото = чекпоинт; изумруд = финиш.");
                notify(player, "Синий лёд = FREEZE; морской фонарь = UNFREEZE.");
                return 1;
            }))
        );
    }

    private static void give(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }
    private static void notify(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal("§b[DDNetCraft] §f" + message), false);
    }

    private static void toggleBlockHook(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (HOOKS.remove(id) != null) {
            notify(player, "Хук отпущен.");
            return;
        }
        if (FROZEN.containsKey(id)) {
            notify(player, "Ты заморожен.");
            return;
        }
        HitResult result = player.raycast(MAX_HOOK_RANGE, 1.0F, false);
        if (result.getType() != HitResult.Type.BLOCK) {
            notify(player, "Нет блока в пределах 26 блоков.");
            return;
        }
        HOOKS.put(id, new Hook(result.getPos(), null, player.getWorld().getRegistryKey(), tick + 200));
    }

    private static void togglePlayerHook(ServerPlayerEntity actor, ServerPlayerEntity target) {
        UUID actorId = actor.getUuid();
        if (HOOKS.remove(actorId) != null) {
            notify(actor, "Хук отпущен.");
            return;
        }
        if (FROZEN.containsKey(actorId) || target.getWorld() != actor.getWorld()
            || actor.squaredDistanceTo(target) > MAX_HOOK_RANGE * MAX_HOOK_RANGE) return;
        if (FROZEN.containsKey(target.getUuid())) {
            unfreeze(target);
            notify(actor, "Товарищ спасён хуком!");
        }
        HOOKS.put(actorId, new Hook(null, target.getUuid(), actor.getWorld().getRegistryKey(), tick + 35));
    }

    private static void serverTick(MinecraftServer server) {
        tick++;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            updateFloor(player);
            Long freezeExpiry = FROZEN.get(id);
            if (freezeExpiry != null) {
                if (freezeExpiry <= tick) unfreeze(player);
                else {
                    player.setVelocity(Vec3d.ZERO);
                    if (tick % 5 == 0)
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 255, false, false, false));
                }
            }
            if (!FROZEN.containsKey(id)) updateHook(player, server);
            Long raceStart = RACES.get(id);
            if (raceStart != null && tick % 10 == 0)
                player.sendMessage(Text.literal("§bDDNet §f| §a" + formatTicks(tick - raceStart)), true);
        }
    }

    private static void updateFloor(ServerPlayerEntity player) {
        Vec3d pos = player.getPos();
        BlockPos underFeet = BlockPos.ofFloored(pos.x, pos.y - 0.2, pos.z);
        UUID id = player.getUuid();
        BlockPos previous = LAST_FLOOR.put(id, underFeet);
        if (underFeet.equals(previous)) return;
        Block block = player.getWorld().getBlockState(underFeet).getBlock();
        if (block == Blocks.BLUE_ICE) {
            if (IMMUNE.getOrDefault(id, 0L) <= tick && !FROZEN.containsKey(id)) {
                FROZEN.put(id, tick + 160);
                HOOKS.remove(id);
                player.setVelocity(Vec3d.ZERO);
                notify(player, "§bFREEZE! §f8 секунд или помощь друга.");
            }
        } else if (block == Blocks.SEA_LANTERN) {
            unfreeze(player);
        } else if (block == Blocks.LIME_CONCRETE) {
            RACES.put(id, tick);
            saveCheckpoint(player);
            notify(player, "§aСтарт! §fТаймер запущен.");
        } else if (block == Blocks.GOLD_BLOCK) {
            if (RACES.containsKey(id)) {
                saveCheckpoint(player);
                notify(player, "§eЧекпоинт сохранён!");
            }
        } else if (block == Blocks.EMERALD_BLOCK) {
            Long start = RACES.remove(id);
            if (start != null) notify(player, "§aФиниш! §fВремя: " + formatTicks(tick - start));
        }
    }
    private static void saveCheckpoint(ServerPlayerEntity player) {
        CHECKPOINTS.put(player.getUuid(), new Checkpoint(
            player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch()));
    }
    private static void unfreeze(ServerPlayerEntity player) {
        if (FROZEN.remove(player.getUuid()) != null) {
            IMMUNE.put(player.getUuid(), tick + 60);
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            notify(player, "§aUNFREEZE! §f3 секунды защиты.");
        }
    }

    private static void updateHook(ServerPlayerEntity actor, MinecraftServer server) {
        UUID id = actor.getUuid();
        Hook hook = HOOKS.get(id);
        if (hook == null) return;
        if (tick >= hook.expires() || !actor.getWorld().getRegistryKey().equals(hook.dimension())) {
            HOOKS.remove(id);
            return;
        }
        Vec3d endpoint;
        if (hook.target() != null) {
            ServerPlayerEntity friend = server.getPlayerManager().getPlayer(hook.target());
            if (friend == null || friend.getWorld() != actor.getWorld()) {
                HOOKS.remove(id);
                return;
            }
            endpoint = actor.getPos().add(0, 1, 0);
            pull(friend, endpoint, 0.34, 1.15);
            if (tick % 3 == 0) drawLine(actor.getServerWorld(), endpoint, friend.getPos().add(0, 1, 0));
        } else {
            endpoint = hook.anchor();
            if (endpoint.squaredDistanceTo(actor.getPos()) > 36 * 36) {
                HOOKS.remove(id);
                return;
            }
            pull(actor, endpoint, 0.30, 1.25);
            if (tick % 3 == 0) drawLine(actor.getServerWorld(), actor.getPos().add(0, 1, 0), endpoint);
        }
    }
    private static void pull(ServerPlayerEntity player, Vec3d toward, double strength, double maxSpeed) {
        Vec3d diff = toward.subtract(player.getPos().add(0, 0.8, 0));
        if (diff.lengthSquared() < 2.25) return;
        Vec3d velocity = player.getVelocity().multiply(0.84).add(diff.normalize().multiply(strength));
        if (velocity.lengthSquared() > maxSpeed * maxSpeed)
            velocity = velocity.normalize().multiply(maxSpeed);
        player.setVelocity(velocity);
        player.fallDistance = 0;
    }
    private static void drawLine(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d vec = to.subtract(from);
        for (int i = 1; i <= 9; i++) {
            Vec3d at = from.add(vec.multiply(i / 10.0));
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
    private static String formatTicks(long elapsed) {
        return String.format(Locale.ROOT, "%.2f сек.", elapsed / 20.0);
    }
    private static void cleanup(UUID id) {
        HOOKS.remove(id);
        FROZEN.remove(id);
        IMMUNE.remove(id);
        CHECKPOINTS.remove(id);
        RACES.remove(id);
        LAST_FLOOR.remove(id);
    }
    private static final class HookItem extends Item {
        HookItem(Settings settings) { super(settings); }
        @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!world.isClient() && user instanceof ServerPlayerEntity actor) toggleBlockHook(actor);
            return TypedActionResult.success(stack, world.isClient());
        }
    }
}
