package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoFall via outgoing-packet spoof.
 *
 * The vanilla server only deals fall damage when a position packet arrives
 * with onGround=true after the server has accumulated fall-distance. By
 * forcing this packet's onGround() accessor to return true whenever the
 * local player is currently falling more than ~2 blocks, the server's
 * fall-distance is reset every tick, so it never reaches the >3-block
 * threshold required to deal damage.
 *
 * The 2-block guard prevents the spoof from firing during normal walking
 * jumps (where fallDistance briefly spikes to ~1.25 on landing) so that
 * tiny jumps don't accidentally desync the server's onGround state.
 */
@Mixin(PlayerMoveC2SPacket.class)
public abstract class PlayerMoveC2SPacketMixin {

    @Inject(method = "onGround", at = @At("HEAD"), cancellable = true)
    private void shieldmacemod$noFall(CallbackInfoReturnable<Boolean> cir) {
        ShieldMaceSettings settings = ShieldMaceSettings.INSTANCE;
        if (!settings.noFallEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        if (mc.player.fallDistance >= 2.0f) {
            cir.setReturnValue(true);
        }
    }
}
