package com.shieldmacemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class ShieldMaceMod implements ClientModInitializer {

    public static KeyBinding openGuiKey;
    public static KeyBinding toggleComboKey;
    public static KeyBinding toggleBreachSwapKey;
    public static KeyBinding toggleMaceSpamKey;

    public static ShieldMaceFeature feature;

    // GLFW key codes (inlined to avoid LWJGL compile-time dependency)
    private static final int GLFW_KEY_UNKNOWN       = -1;
    private static final int GLFW_KEY_RIGHT_SHIFT   = 344;
    private static final int GLFW_KEY_RIGHT_CONTROL = 345;
    private static final int GLFW_KEY_RIGHT_ALT     = 346;

    @Override
    public void onInitializeClient() {
        // Right Shift now opens the in-game configuration GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.openGui",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        // Per-feature shortcut toggles (rebindable in vanilla controls AND in the GUI)
        toggleComboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleCombo",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC
        ));

        toggleBreachSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleBreachSwap",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_CONTROL,
                KeyBinding.Category.MISC
        ));

        toggleMaceSpamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleMaceSpam",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_ALT,
                KeyBinding.Category.MISC
        ));

        feature = new ShieldMaceFeature();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                client.setScreen(new ShieldMaceGui());
            }
            while (toggleComboKey.wasPressed()) {
                feature.toggleCombo(client);
            }
            while (toggleBreachSwapKey.wasPressed()) {
                feature.toggleBreachSwap(client);
            }
            while (toggleMaceSpamKey.wasPressed()) {
                feature.toggleMaceSpam(client);
            }
            feature.tick(client);
        });
    }
}
