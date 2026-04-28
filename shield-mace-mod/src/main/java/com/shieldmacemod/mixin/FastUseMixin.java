package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FastUse — accelerate the local player's item-use timer so that food,
 * potions, ender pearls etc. activate after a single tick instead of
 * the vanilla 32-tick wait. The timer is driven by
 * {@code itemUseTimeLeft}, decremented once per tick by the private
 * {@code tickActiveItemStack()} method. We force it down to 1 at HEAD,
 * so the next tick of normal item-use processing instantly completes
 * the usage.
 *
 * Only fires for the local player — other LivingEntities are unaffected.
 */
@Mixin(LivingEntity.class)
public abstract class FastUseMixin {

    @Inject(method = "tickActiveItemStack", at = @At("HEAD"))
    private void shieldmacemod$fastUse(CallbackInfo ci) {
        if (!ShieldMaceSettings.INSTANCE.fastUseEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        Object self = this;
        if (self != mc.player) return;

        LivingEntity le = (LivingEntity) self;
        if (le.getItemUseTimeLeft() > 1) {
            ((FastUseAccessor) le).shieldmacemod$setItemUseTimeLeft(1);
        }
    }
}
