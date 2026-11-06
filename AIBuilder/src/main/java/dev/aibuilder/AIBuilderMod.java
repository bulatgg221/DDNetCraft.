package dev.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Minecraft singleplayer prompt-to-blocks builder. Uses the normal integrated server only. */
public final class AIBuilderMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("AIBuilder");
    private static BuilderConfig config;

    public static BuilderConfig config() { return config; }

    @Override
    public void onInitialize() {
        config = BuilderConfig.load();
        BuildService.INSTANCE.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BuildService.INSTANCE.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("help").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BuildService.tell(player, "/ai build <описание> — ИИ строит по запросу (нужен API-ключ).");
                    BuildService.tell(player, "/ai demo — тестовая постройка без API-ключа.");
                    BuildService.tell(player, "/ai undo, /ai cancel, /ai status, /ai setup, /ai reload");
                    return 1;
                }))
                .then(CommandManager.literal("setup").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BuildService.tell(player, "Укажи API-ключ в config/aibuilder/api-key.txt (одной строкой).");
                    BuildService.tell(player, "Модель: " + config.model + "; настройка config/aibuilder/config.json.");
                    BuildService.tell(player, "OpenAI API оплачивается отдельно от подписки ChatGPT.");
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(ctx -> {
                    BuildService.INSTANCE.status(ctx.getSource().getPlayerOrThrow()); return 1;
                }))
                .then(CommandManager.literal("cancel").executes(ctx -> {
                    BuildService.INSTANCE.cancel(ctx.getSource().getPlayerOrThrow()); return 1;
                }))
                .then(CommandManager.literal("undo").executes(ctx -> {
                    BuildService.INSTANCE.undo(ctx.getSource().getPlayerOrThrow()); return 1;
                }))
                .then(CommandManager.literal("reload").executes(ctx -> {
                    config = BuilderConfig.load();
                    BuildService.tell(ctx.getSource().getPlayerOrThrow(), "Конфигурация перечитана.");
                    return 1;
                }))
                .then(CommandManager.literal("demo").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    MinecraftServer server = ctx.getSource().getServer();
                    if (!allowed(player, server)) return 0;
                    if (BuildService.INSTANCE.isBusy()) { BuildService.tell(player, "Сначала завершите предыдущую стройку."); return 0; }
                    try (InputStream in = AIBuilderMod.class.getResourceAsStream("/assets/aibuilder/plans/demo_spawn.json")) {
                        if (in == null) throw new IllegalStateException("Демонстрационный план отсутствует.");
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        PlanCompiler.CompiledPlan plan = PlanCompiler.compile(json, player.getBlockPos(), config);
                        BuildService.INSTANCE.schedule(server, player, plan);
                        return 1;
                    } catch (Exception e) {
                        BuildService.tell(player, "Ошибка demo: " + shortMessage(e));
                        return 0;
                    }
                }))
                .then(CommandManager.literal("build")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString()).executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        MinecraftServer server = ctx.getSource().getServer();
                        if (!allowed(player, server)) return 0;
                        String prompt = StringArgumentType.getString(ctx, "prompt").strip();
                        if (prompt.isEmpty() || prompt.length() > 1800) {
                            BuildService.tell(player, "Укажи описание длиной 1–1800 символов."); return 0;
                        }
                        BuildService service = BuildService.INSTANCE;
                        int ticket = service.beginPlanning();
                        if (ticket < 0) { BuildService.tell(player, "Другая постройка ещё обрабатывается."); return 0; }
                        BlockPos anchor = player.getBlockPos().toImmutable();
                        var worldKey = player.getWorld().getRegistryKey();
                        BuildService.tell(player, "Генерирую архитектурный план… /ai status для проверки; /ai cancel для отмены.");
                        BuilderConfig snapshot = config;
                        OpenAIPlanner.plan(prompt, snapshot).whenComplete((json, error) -> server.execute(() -> {
                            if (!service.isPending(ticket)) return;
                            service.endPlanning(ticket);
                            ServerPlayerEntity current = server.getPlayerManager().getPlayer(player.getUuid());
                            if (current == null || !current.getWorld().getRegistryKey().equals(worldKey)) return;
                            if (error != null) {
                                BuildService.tell(current, "Ошибка OpenAI API: " + shortMessage(error));
                                return;
                            }
                            try {
                                PlanCompiler.CompiledPlan plan = PlanCompiler.compile(json, anchor, snapshot);
                                service.schedule(server, current, plan);
                            } catch (Exception ex) {
                                BuildService.tell(current, "План отклонён: " + shortMessage(ex));
                                BuildService.tell(current, "Попробуй более конкретный или небольшой проект.");
                            }
                        }));
                        return 1;
                    })))
                .executes(ctx -> {
                    BuildService.tell(ctx.getSource().getPlayerOrThrow(),
                        "Напиши /ai help. Пример: /ai build небольшой японский храм из дерева и камня");
                    return 1;
                })
            );
        });
        LOG.info("AI Builder singleplayer mod initialized");
    }

    private static boolean allowed(ServerPlayerEntity player, MinecraftServer server) {
        if (server.isDedicated()) {
            BuildService.tell(player, "Этот MVP поддерживает только одиночный мир, не выделенные серверы.");
            return false;
        }
        if (!player.isCreative()) {
            BuildService.tell(player, "Переключись в творческий режим для изменения мира.");
            return false;
        }
        return true;
    }
    private static String shortMessage(Throwable error) {
        Throwable e = error;
        while (e.getCause() != null && e.getCause() != e) e = e.getCause();
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
