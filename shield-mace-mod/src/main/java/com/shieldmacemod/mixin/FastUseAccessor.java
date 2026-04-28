package com.shieldmacemod.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the package-private itemUseTimeLeft field for FastUse. */
@Mixin(LivingEntity.class)
public interface FastUseAccessor {
    @Accessor("itemUseTimeLeft")
    void shieldmacemod$setItemUseTimeLeft(int value);
}
