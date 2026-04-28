package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Criticals — fire a fake hop sequence right before every melee attack
 * so the server thinks the player is airborne and applies the vanilla
 * critical-hit multiplier. The hop is tiny (~0.0625 b) so it never
 * trips a movement-too-fast check, and the player's tracked position
 * is restored on the third packet so reach is preserved.
 *
 * Only fires when the player is on the ground; if they're already
 * falling vanilla criticals already apply.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class CriticalsMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void shieldmacemod$criticals(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!ShieldMaceSettings.INSTANCE.criticalsEnabled) return;
        if (!(player instanceof ClientPlayerEntity cp)) return;
        if (cp.networkHandler == null) return;
        if (!cp.isOnGround()) return;
        if (cp.isClimbing() || cp.isTouchingWater() || cp.isInLava()) return;
        if (cp.hasVehicle()) return;

        double x = cp.getX(), y = cp.getY(), z = cp.getZ();
        var net = cp.networkHandler;
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0001, z, false, false));
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
    }
}
