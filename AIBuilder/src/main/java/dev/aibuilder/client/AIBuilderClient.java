package dev.aibuilder.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Safely opens the local API-key window once the chat screen has closed.
 * F8 is a fallback in launchers (such as LabyMod) that handle client commands differently.
 * The key itself never appears in command arguments, chat messages or packet contents.
 */
public final class AIBuilderClient implements ClientModInitializer {
    private boolean openRequested = false;
    private KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.aibuilder.api_key",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.category.aibuilder"
        ));

        // /aikey is local; the secret must NEVER be typed as part of the command.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("aikey").executes(context -> {
                openRequested = true;
                return 1;
            }))
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                openRequested = true;
            }
            // Minecraft processes /aikey before dismissing the chat screen; opening here
            // on a subsequent client tick prevents ChatScreen from immediately closing us.
            if (openRequested && client.player != null && client.currentScreen == null) {
                openRequested = false;
                client.setScreen(new ApiKeyScreen());
            }
            if (client.player == null) openRequested = false;
        });
    }
}
