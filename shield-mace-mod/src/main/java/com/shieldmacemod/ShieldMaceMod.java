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
    public static KeyBinding togglePearlInterceptKey;
    public static KeyBinding toggleSilentAimKey;
    public static KeyBinding toggleHeightSmashKey;
    public static KeyBinding toggleHitboxExpandKey;
    public static KeyBinding toggleAutoTotemKey;
    public static KeyBinding toggleNoFallKey;
    public static KeyBinding toggleKillAuraKey;
    public static KeyBinding toggleFlightKey;

    public static ShieldMaceFeature feature;
    public static PearlInterceptor   pearlInterceptor;

    // GLFW key codes (inlined to avoid LWJGL compile-time dependency)
    private static final int GLFW_KEY_UNKNOWN       = -1;
    private static final int GLFW_KEY_RIGHT_SHIFT   = 344;
    private static final int GLFW_KEY_RIGHT_CONTROL = 345;
    private static final int GLFW_KEY_RIGHT_ALT     = 346;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.openGui",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC));

        toggleComboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleCombo",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleBreachSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleBreachSwap",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_CONTROL,
                KeyBinding.Category.MISC));

        toggleMaceSpamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleMaceSpam",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_RIGHT_ALT,
                KeyBinding.Category.MISC));

        togglePearlInterceptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.togglePearlIntercept",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleSilentAimKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleSilentAim",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleHeightSmashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleHeightSmash",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleHitboxExpandKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleHitboxExpand",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleAutoTotemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleAutoTotem",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleNoFallKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleNoFall",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleKillAuraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleKillAura",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        toggleFlightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod.toggleFlight",
                InputUtil.Type.KEYSYM,
                GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC));

        feature          = new ShieldMaceFeature();
        pearlInterceptor = new PearlInterceptor();

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
            while (togglePearlInterceptKey.wasPressed()) {
                pearlInterceptor.toggle(client);
            }
            while (toggleSilentAimKey.wasPressed()) {
                feature.toggleSilentAim(client);
            }
            while (toggleHeightSmashKey.wasPressed()) {
                feature.toggleHeightSmash(client);
            }
            while (toggleHitboxExpandKey.wasPressed()) {
                feature.toggleHitboxExpand(client);
            }
            while (toggleAutoTotemKey.wasPressed()) {
                feature.toggleAutoTotem(client);
            }
            while (toggleNoFallKey.wasPressed()) {
                feature.toggleNoFall(client);
            }
            while (toggleKillAuraKey.wasPressed()) {
                feature.toggleKillAura(client);
            }
            while (toggleFlightKey.wasPressed()) {
                feature.toggleFlight(client);
            }
            feature.tick(client);
            pearlInterceptor.tick(client);
        });
    }
}
