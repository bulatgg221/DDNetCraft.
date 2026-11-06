package dev.ddnetcraft.mixin;

import dev.ddnetcraft.client.DDNetClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only cancels vanilla motion while the local singleplayer DDNet mode is active. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMovementMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void ddnetcraft$customTravel(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        DDNetClient ddnet = DDNetClient.INSTANCE;
        if (ddnet != null && ddnet.interceptMovement(mc, (ClientPlayerEntity)(Object)this)) {
            ddnet.movementTick(mc);
            ci.cancel();
        }
    }
}
