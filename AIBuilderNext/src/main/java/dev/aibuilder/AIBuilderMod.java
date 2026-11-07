package dev.aibuilder;
import dev.aibuilder.config.*;
import dev.aibuilder.world.*;
import dev.aibuilder.planning.ProjectStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;

public final class AIBuilderMod implements ModInitializer {
    public static volatile BuilderConfig config;
    public static BuildService service;
    public void onInitialize(){try{config=BuilderConfig.load();}catch(Exception e){config=new BuilderConfig();LoggerFactory.getLogger("AIBuilder").warn("Invalid AI Builder config; using defaults.");}
        service=new BuildService();
        if(Boolean.getBoolean("aibuilder.smoke")) {
            try{String report=BuilderSelfTest.run(service.materials);LoggerFactory.getLogger("AIBuilder").info("SMOKE PASSED: "+report);System.exit(0);}
            catch(Throwable failure){LoggerFactory.getLogger("AIBuilder").error("SMOKE FAILED",failure);System.exit(1);}
        }
        ServerTickEvents.END_SERVER_TICK.register(s->service.tick(s,config));ServerLifecycleEvents.SERVER_STOPPED.register(s->service.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=CommandManager.literal("ai").requires(src->!src.getServer().isDedicated()&&src.hasPermissionLevel(2));
            root.then(CommandManager.literal("build").then(CommandManager.argument("description",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->service.generate(p,StringArgumentType.getString(ctx,"description"),config)))));
            root.then(CommandManager.literal("help").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->tell(p,"F7 — описание/предпросмотр, F8 — API. /ai build, confirm, cancel, undo, redo, status, selftest, config reload, history, saveplan, loadplan. Новый запрос оплачивается отдельно."))));
            root.then(CommandManager.literal("version").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->tell(p,"AI Builder Next 1.0.0-alpha.1 / Minecraft 1.21.1"))));
            root.then(CommandManager.literal("status").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->tell(p,service.status))));
            root.then(CommandManager.literal("confirm").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),service::confirm)));
            root.then(CommandManager.literal("cancel").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->service.cancel())));
            root.then(CommandManager.literal("undo").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),service.queue::undo)));
            root.then(CommandManager.literal("redo").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),service.queue::redo)));
            root.then(CommandManager.literal("selftest").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->{String report=BuilderSelfTest.run(service.materials);tell(p,report);LoggerFactory.getLogger("AIBuilder").info(report);})));
            root.then(CommandManager.literal("config").then(CommandManager.literal("reload").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->{config=BuilderConfig.load();tell(p,"Настройки обновлены.");}))));
            root.then(CommandManager.literal("history").executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->tell(p,String.join(", ",ProjectStore.list())))));
            root.then(CommandManager.literal("saveplan").then(CommandManager.argument("name",StringArgumentType.word()).executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->{var v=service.preview;if(v==null)throw new IllegalArgumentException("Сначала создайте план.");ProjectStore.save(StringArgumentType.getString(ctx,"name"),v.prompt(),v.model(),v.plan());tell(p,"План сохранён.");}))));
            root.then(CommandManager.literal("loadplan").then(CommandManager.argument("name",StringArgumentType.word()).executes(ctx->run(ctx.getSource().getPlayerOrThrow(),p->service.load(p,StringArgumentType.getString(ctx,"name"),config)))));
            dispatcher.register(root);
        });
    }
    public static int run(ServerPlayerEntity p,java.util.function.Consumer<ServerPlayerEntity> action){try{action.accept(p);return 1;}catch(Exception e){service.status=e.getMessage();tell(p,e.getMessage());return 0;}}
    public static void tell(ServerPlayerEntity p,String s){p.sendMessage(Text.literal("[AI Builder] "+s),false);}
}
