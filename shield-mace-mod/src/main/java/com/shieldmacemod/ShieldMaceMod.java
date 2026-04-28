package com.shieldmacemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class ShieldMaceMod implements ClientModInitializer {

    public static KeyBinding toggleKey;
    public static KeyBinding toggleBreachSwapKey;

    // GLFW key codes (inlined to avoid LWJGL compile-time dependency)
    private static final int GLFW_KEY_RIGHT_SHIFT   = 344;
    private static final int GLFW_KEY_RIGHT_CONTROL = 345;

    @Override
    public void onInitializeClient() {
        // Appears in Options → Controls → Shield Mace Mod and is rebindable
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggle",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        toggleBreachSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleBreachSwap",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_CONTROL,
                KeyBinding.Category.MISC
        ));

        ShieldMaceFeature feature = new ShieldMaceFeature();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                feature.toggle(client);
            }
            while (toggleBreachSwapKey.wasPressed()) {
                feature.toggleBreachSwap(client);
            }
            feature.tick(client);
        });
    }
}
