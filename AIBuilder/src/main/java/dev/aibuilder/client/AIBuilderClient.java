package dev.aibuilder.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Local-only command. The secret itself is never passed through chat,
 * Brigadier command arguments, server packets or chat history.
 */
public final class AIBuilderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("aikey").executes(context -> {
                MinecraftClient client = context.getSource().getClient();
                client.execute(() -> client.setScreen(new ApiKeyScreen()));
                return 1;
            }))
        );
    }
}
