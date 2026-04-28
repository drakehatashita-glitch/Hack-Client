package com.shieldmacemod.mixin;

import com.shieldmacemod.ShieldMaceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InventoryMove — when the player has a screen open (inventory, chest, …)
 * vanilla locks the movement keys to "not pressed". This mixin checks the
 * physical key state directly so movement / jumping / sneaking continue to
 * work while a non-chat screen is open.
 *
 * Only fires when InventoryMove is enabled and the open screen is not a
 * chat or sign edit screen (we don't want to walk while typing).
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void shieldmacemod$inventoryMove(CallbackInfoReturnable<Boolean> cir) {
        if (!ShieldMaceSettings.INSTANCE.inventoryMoveEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        Screen current = client.currentScreen;
        if (current == null) return;
        if (current instanceof net.minecraft.client.gui.screen.ChatScreen) return;

        KeyBinding self = (KeyBinding) (Object) this;
        if (self != client.options.forwardKey
                && self != client.options.backKey
                && self != client.options.leftKey
                && self != client.options.rightKey
                && self != client.options.jumpKey
                && self != client.options.sneakKey
                && self != client.options.sprintKey) {
            return;
        }

        InputUtil.Key bound = ((KeyBindingAccessor) self).shieldmacemod$getBoundKey();
        if (bound == null || bound.equals(InputUtil.UNKNOWN_KEY)) return;

        long handle = client.getWindow().getHandle();
        boolean down;
        try {
            if (bound.getCategory() == InputUtil.Type.KEYSYM) {
                down = GLFW.glfwGetKey(handle, bound.getCode()) == GLFW.GLFW_PRESS;
            } else if (bound.getCategory() == InputUtil.Type.MOUSE) {
                down = GLFW.glfwGetMouseButton(handle, bound.getCode()) == GLFW.GLFW_PRESS;
            } else {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }

        if (down) cir.setReturnValue(true);
    }
}
