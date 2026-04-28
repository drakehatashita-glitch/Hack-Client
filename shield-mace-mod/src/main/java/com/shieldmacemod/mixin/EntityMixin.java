package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side hitbox expander.
 *
 * Overrides {@link Entity#getTargetingMargin()} for OTHER players so that the
 * client's raycast hit-test (used for crosshair targeting and attacks) treats
 * their bounding box as enlarged by the configured number of blocks. The
 * collision box is unaffected, so players are not pushed around.
 *
 * The local player and non-player entities are intentionally left alone.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
    private void shieldmacemod$expandHitbox(CallbackInfoReturnable<Float> cir) {
        ShieldMaceSettings settings = ShieldMaceSettings.INSTANCE;
        if (!settings.hitboxExpandEnabled) return;

        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || self == client.player) return;

        float expansion = Math.max(1, Math.min(50, settings.hitboxExpandTenths)) / 10.0f;
        cir.setReturnValue(expansion);
    }
}
