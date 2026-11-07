package dev.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.Set;
import java.util.Locale;
import dev.aibuilder.client.AIBuilderClient;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
    public static void applyConfig(BuilderConfig updated) { config = updated; }

    @Override
    public void onInitialize() {
        config = BuilderConfig.load();
        BuildService.INSTANCE.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BuildService.INSTANCE.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("selftest").executes(ctx -> {
                    ServerPlayerEntity player=ctx.getSource().getPlayerOrThrow();
                    MinecraftServer server=ctx.getSource().getServer();
                    if(!allowed(player,server))return 0;
                    BuilderSelfTest.Result report=BuilderSelfTest.run(config);
                    BuildService.tell(player,"Проверка AI Builder: "
                        +report.paletteChecked()+" ID блоков Minecraft и "
                        +report.scenariosChecked()+" тестовых геометрий.");
                    if(report.success()) {
                        BuildService.tell(player,
                            "Тест пройден: неизвестных блоков и ошибок сборки образцов не найдено.");
                        return 1;
                    }
                    BuildService.tell(player,"Найдено ошибок: "+report.failures().size());
                    for(int i=0;i<Math.min(8,report.failures().size());i++)
                        BuildService.tell(player,(i+1)+": "+report.failures().get(i));
                    AIBuilderMod.LOG.warn("AI Builder selftest failures: {}",report.failures());
                    return 0;
                }))
                .then(CommandManager.literal("version").executes(ctx -> {
                    String version = FabricLoader.getInstance().getModContainer("aibuilder")
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("неизвестно");
                    BuildService.tell(ctx.getSource().getPlayerOrThrow(),
                        "AI Builder v" + version + " загружен. Проверь, что нет старых JAR в mods.");
                    return 1;
                }))
                .then(CommandManager.literal("help").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BuildService.tell(player, "/ai build <описание> — ИИ строит по запросу (нужен API-ключ).");
                    BuildService.tell(player, "/ai demo — спавн; /ai house <style> — 7 стилей домов; /ai castle — крепость без API.");
                    BuildService.tell(player, "/ai version — какая версия мода действительно запущена.");
                    BuildService.tell(player, "/ai selftest — проверить палитру и операции без постройки и API.");
                    BuildService.tell(player, "/ai undo, /ai cancel, /ai status, /ai setup, /ai reload");
                    BuildService.tell(player, "/ai prompt или F7 — окно подробного промпта до 12000 символов.");
                    BuildService.tell(player, "/ai key или F8 — выбор Polza AI, модели и ввод ключа.");
                    return 1;
                }))

                .then(CommandManager.literal("prompt").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (ctx.getSource().getServer().isDedicated()) {
                        BuildService.tell(player, "Только для одиночного мира."); return 0;
                    }
                    AIBuilderClient.requestPromptOpen();
                    BuildService.tell(player, "Открываю редактор промпта. Если не появился — нажми F7.");
                    return 1;
                }))
                .then(CommandManager.literal("key").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (ctx.getSource().getServer().isDedicated()) {
                        BuildService.tell(player, "Это окно доступно только в одиночном мире."); return 0;
                    }
                    AIBuilderClient.requestOpen();
                    BuildService.tell(player, "Открываю настройки API. Если окно не появилось — нажми F8.");
                    return 1;
                }))
                .then(CommandManager.literal("setup").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (!ctx.getSource().getServer().isDedicated()) AIBuilderClient.requestOpen();
                    BuildService.tell(player, "Настройки API: /ai key или F8.");
                    BuildService.tell(player, "Провайдер: " + BuilderConfig.providerName(config.provider) + "; модель: " + config.model + ".");
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
                .then(CommandManager.literal("castle").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    MinecraftServer server = ctx.getSource().getServer();
                    if (!allowed(player,server)) return 0;
                    if (BuildService.INSTANCE.isBusy()) {
                        BuildService.tell(player,"Сначала заверши предыдущую стройку."); return 0;
                    }
                    try {
                        JsonObject root = new JsonObject();
                        root.addProperty("name","Castle — four towers and a central keep");
                        JsonObject castle = new JsonObject();
                        castle.addProperty("type","castle");
                        castle.addProperty("x",6);castle.addProperty("y",0);castle.addProperty("z",6);
                        castle.addProperty("w",35);castle.addProperty("d",35);
                        JsonArray operations = new JsonArray();
                        operations.add(castle);
                        root.add("operations",operations);
                        PlanCompiler.CompiledPlan plan = PlanCompiler.compile(
                            root.toString(), player.getBlockPos(),config);
                        BuildService.INSTANCE.schedule(server,player,plan);
                        return 1;
                    } catch (Exception e) {
                        BuildService.tell(player,"Ошибка castle: "+shortMessage(e));
                        return 0;
                    }
                }))
                .then(CommandManager.literal("house")
                    .executes(ctx -> buildStyleDemo(ctx.getSource().getPlayerOrThrow(), "medieval"))
                    .then(CommandManager.argument("style", StringArgumentType.word())
                        .executes(ctx -> buildStyleDemo(ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx,"style")))))
                .then(CommandManager.literal("build")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString()).executes(ctx -> {
                        return startBuild(ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "prompt"));

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

    private static int buildStyleDemo(ServerPlayerEntity player,String requestedStyle) {
        MinecraftServer server=player.getServer();
        if(server==null || !allowed(player,server))return 0;
        if(BuildService.INSTANCE.isBusy()) {
            BuildService.tell(player, "Сначала заверши предыдущую постройку.");
            return 0;
        }
        String style=requestedStyle.toLowerCase(Locale.ROOT);
        if(!Set.of("medieval","modern","japanese","nordic","alpine","rustic","brick").contains(style)){
            BuildService.tell(player, "Стиль: medieval, modern, japanese, nordic, alpine, rustic, brick.");
            return 0;
        }
        try (InputStream in=AIBuilderMod.class.getResourceAsStream("/assets/aibuilder/plans/demo_house.json")) {
            if(in==null)throw new IllegalStateException("Демонстрационный план отсутствует.");
            JsonObject root=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject house=root.getAsJsonArray("operations").get(0).getAsJsonObject();
            house.addProperty("style",style);
            // Preset demonstration styles should use their own default palettes.
            for(String paletteKey: Set.of("foundation","floor","wall","beam","roof","roof_block","door","glass"))
                house.remove(paletteKey);
            switch(style){
                case "modern","brick" -> {
                    house.addProperty("w",23);house.addProperty("d",17);
                }
                case "alpine" -> {
                    house.addProperty("w",21);house.addProperty("d",17);
                }
                case "nordic" -> {
                    house.addProperty("w",19);house.addProperty("d",15);
                }
                case "japanese" -> {
                    house.addProperty("w",19);house.addProperty("d",13);
                    house.addProperty("floors",1);
                }
                case "rustic" -> {
                    house.addProperty("w",13);house.addProperty("d",11);
                    house.addProperty("floors",1);
                }
                default -> { }
            }
            root.addProperty("name","AI Builder: "+style+" style demo");
            PlanCompiler.CompiledPlan plan=PlanCompiler.compile(root.toString(),
                player.getBlockPos(),config);
            BuildService.INSTANCE.schedule(server,player,plan);
            return 1;
        } catch(Exception e){
            BuildService.tell(player,"Ошибка house: "+shortMessage(e));
            return 0;
        }
    }

    /** Runs on the integrated-server thread; also invoked by the client's multiline GUI. */
    public static int startBuild(ServerPlayerEntity player, String prompt) {
        MinecraftServer server = player.getServer();
        if (server == null || !allowed(player,server)) return 0;

                        
                        
                        if (!allowed(player, server)) return 0;
                        prompt = prompt.strip();
                        if (prompt.isEmpty() || prompt.length() > PromptLimits.MAX_CHARS) {
                            BuildService.tell(player, "Укажи описание длиной 1–" + PromptLimits.MAX_CHARS + " символов."); return 0;
                        }
                        BuildService service = BuildService.INSTANCE;
                        int ticket = service.beginPlanning();
                        if (ticket < 0) { BuildService.tell(player, "Другая постройка ещё обрабатывается."); return 0; }
                        BlockPos anchor = player.getBlockPos().toImmutable();
                        var worldKey = player.getWorld().getRegistryKey();
                        BuildService.tell(player, "ИИ создаёт оригинальный чертёж с координатами… /ai status для проверки; /ai cancel для отмены.");
                        BuilderConfig snapshot = config;
                        final String designBrief = prompt;
                        OpenAIPlanner.plan(prompt, snapshot).whenComplete((json, error) -> server.execute(() -> {
                            if (!service.isPending(ticket)) return;
                            service.endPlanning(ticket);
                            ServerPlayerEntity current = server.getPlayerManager().getPlayer(player.getUuid());
                            if (current == null || !current.getWorld().getRegistryKey().equals(worldKey)) return;
                            if (error != null) {
                                BuildService.tell(current, "Ошибка генерации через "
                                    + BuilderConfig.providerName(snapshot.provider)
                                    + ": " + shortMessage(error));
                                return;
                            }
                            try {
                                // Freeform builds use the real model-generated coordinates.
                                // Prebuilt HouseLayouts / CastleLayouts are reserved for /ai
                                // house and /ai castle tests only.
                                GeneratedPlanGuard.requireOriginalGeometry(json);
                                PlanCompiler.CompiledPlan plan = PlanCompiler.compile(
                                    json, anchor, snapshot);
                                if(ArtworkMode.isArtwork(designBrief)){
                                    ArtworkMode.requireArtworkGeometry(designBrief,json,
                                        MaterialCatalog.allowedIds());
                                    JsonArray drawings=JsonParser.parseString(json)
                                        .getAsJsonObject().getAsJsonArray("operations");
                                    for(var drawing:drawings){
                                        if(!drawing.isJsonObject())continue;
                                        JsonObject image=drawing.getAsJsonObject();
                                        if(!image.has("type") ||
                                            !image.get("type").getAsString()
                                                .equalsIgnoreCase("pixel_art"))continue;
                                        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
                                            image,MaterialCatalog.allowedIds());
                                        BuildService.tell(current,"ИИ нарисовал оригинальную "
                                            +"цветную мозаику "+canvas.width()+"×"
                                            +canvas.height()+" из "+canvas.colorCount()
                                            +" оттенков ("+canvas.paintedPixels()+" пикселей).");
                                    }
                                }
                                GeneratedPlanArchive.trySave(json);
                                BuildService.tell(current, "Оригинальный чертёж ИИ: "
                                    + plan.operations() + " операций, "
                                    + plan.blocks().size() + " блоков. "
                                    + "План сохранён в config/aibuilder/last-generated-plan.json.");
                                service.schedule(server, current, plan);
                            } catch (Exception ex) {
                                BuildService.tell(current, "План отклонён: " + shortMessage(ex));
                                BuildService.tell(current, "Попробуй более конкретный или небольшой проект.");
                            }
                        }));
                        return 1;
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
