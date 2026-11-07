package dev.aibuilder.client;
import dev.aibuilder.AIBuilderMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

public final class AIBuilderClient implements ClientModInitializer {
    public void onInitializeClient(){KeyBinding prompt=KeyBindingHelper.registerKeyBinding(new KeyBinding("AI Builder: проект",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F7,"AI Builder"));KeyBinding settings=KeyBindingHelper.registerKeyBinding(new KeyBinding("AI Builder: настройки",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"AI Builder"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{while(prompt.wasPressed())client.setScreen(AIBuilderMod.service.preview!=null?new PreviewScreen():new PromptScreen());while(settings.wasPressed())client.setScreen(new SettingsScreen());});}
    public static void server(Consumer<ServerPlayerEntity> task){var c=MinecraftClient.getInstance();if(c.getServer()==null||c.player==null){AIBuilderMod.service.status="Доступно в одиночном мире.";return;}var s=c.getServer();var id=c.player.getUuid();s.execute(()->{var p=s.getPlayerManager().getPlayer(id);if(p!=null)AIBuilderMod.run(p,task);});}
}
