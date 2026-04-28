package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-Knockback — cancel server-sent velocity updates that target the
 * local player. The vanilla server pushes the player on hit by sending
 * an {@link EntityVelocityUpdateS2CPacket} with the new velocity; we
 * silently drop those packets so the player isn't knocked back.
 *
 * Updates targeting other entities are passed through normally.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class AntiKnockbackMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void shieldmacemod$antiKb(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (!ShieldMaceSettings.INSTANCE.antiKnockbackEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        if (packet.getEntityId() == mc.player.getId()) {
            ci.cancel();
        }
    }
}
