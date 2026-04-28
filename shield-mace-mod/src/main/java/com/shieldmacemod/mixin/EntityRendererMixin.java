package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F3+B integration for Hitbox Expander.
 *
 * Vanilla's {@code EntityRenderer.renderHitbox} draws the white debug box
 * (and the red eye-line) when F3+B is on. We piggy-back on that exact draw
 * call: at the TAIL of renderHitbox, when the entity is another player and
 * Hitbox Expander is on, we draw a second box in cyan that matches the
 * inflated targeting hitbox the {@link com.shieldmacemod.mixin.EntityMixin}
 * exposes to the client raycaster. The matrix stack and vertex consumer are
 * already set up by vanilla, so the box will only ever appear when the
 * vanilla hitbox is visible (i.e. F3+B is enabled).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "renderHitbox", at = @At("TAIL"))
    private static void shieldmacemod$drawExpandedHitbox(
            MatrixStack matrices,
            VertexConsumer vertices,
            Entity entity,
            float tickProgress,
            float red,
            float green,
            float blue,
            CallbackInfo ci) {
        ShieldMaceSettings settings = ShieldMaceSettings.INSTANCE;
        if (!settings.hitboxExpandEnabled) return;
        if (!(entity instanceof PlayerEntity)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || entity == mc.player) return;

        float expansion = Math.max(1, Math.min(50, settings.hitboxExpandTenths)) / 10.0f;

        // Vanilla translates the matrix to the entity's interpolated position
        // before calling renderHitbox, so the bounding box must be
        // re-expressed relative to (entity.x, entity.y, entity.z).
        Box box = entity.getBoundingBox()
                .offset(-entity.getX(), -entity.getY(), -entity.getZ())
                .expand(expansion);

        VertexRendering.drawBox(matrices, vertices, box, 0.0f, 1.0f, 1.0f, 1.0f);
    }
}
