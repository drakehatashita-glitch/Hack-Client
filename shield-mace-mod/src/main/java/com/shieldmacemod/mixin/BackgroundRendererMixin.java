package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright (night-vision strength override).
 *
 * Forces the GameRenderer's reported night-vision strength to 1.0 whenever
 * Fullbright is on. Combined with the gamma boost applied each tick from
 * {@code ShieldMaceFeature.tickFullbright}, this washes the lightmap out
 * to maximum brightness so dark caves are fully lit.
 */
@Mixin(GameRenderer.class)
public abstract class BackgroundRendererMixin {

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void shieldmacemod$fullbright(
            net.minecraft.entity.LivingEntity entity, float tickProgress,
            CallbackInfoReturnable<Float> cir) {
        if (ShieldMaceSettings.INSTANCE.fullbrightEnabled) {
            cir.setReturnValue(1.0f);
        }
    }
}
