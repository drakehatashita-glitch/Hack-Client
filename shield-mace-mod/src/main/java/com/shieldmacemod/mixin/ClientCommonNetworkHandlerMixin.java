package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceMod;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blink — outbound packet interceptor.
 *
 * While the Blink toggle is on, every {@link net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket}
 * the client tries to send is queued inside {@link com.shieldmacemod.ShieldMaceFeature}
 * and the original {@code sendPacket} call is cancelled, so the server
 * never sees the local player move. When the toggle is turned off, the
 * feature's flush logic re-sends every queued packet through this same
 * {@code sendPacket} method while a bypass flag is held, so the server
 * fast-forwards the player's tracked position to where they actually are.
 *
 * Targets {@link ClientCommonNetworkHandler} (not the {@code ClientPlay…}
 * subclass) because that's where the public {@code sendPacket(Packet)}
 * method is declared in 1.21.x — mixing into the subclass would silently
 * fail to find an override that doesn't exist in bytecode.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void shieldmacemod$blinkIntercept(Packet<?> packet, CallbackInfo ci) {
        if (ShieldMaceMod.feature == null) return;
        if (ShieldMaceMod.feature.queueBlinkPacket(packet)) {
            ci.cancel();
        }
    }
}
