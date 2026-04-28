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
    public static KeyBinding toggleBlinkKey;

    // Movement
    public static KeyBinding toggleSpeedKey;
    public static KeyBinding toggleSprintKey;
    public static KeyBinding toggleStepKey;
    public static KeyBinding toggleLongJumpKey;
    public static KeyBinding toggleJesusKey;
    public static KeyBinding toggleSpiderKey;
    public static KeyBinding toggleGlideKey;

    // Combat
    public static KeyBinding toggleTriggerBotKey;
    public static KeyBinding toggleAutoClickerKey;
    public static KeyBinding toggleCriticalsKey;
    public static KeyBinding toggleReachKey;
    public static KeyBinding toggleAntiKnockbackKey;

    // Player Assist
    public static KeyBinding toggleAutoArmorKey;
    public static KeyBinding toggleFastUseKey;
    public static KeyBinding toggleInventoryMoveKey;
    public static KeyBinding toggleChestStealerKey;

    // Render
    public static KeyBinding toggleXrayKey;
    public static KeyBinding toggleEspKey;
    public static KeyBinding toggleTracersKey;
    public static KeyBinding toggleFullbrightKey;
    public static KeyBinding toggleNameTagsKey;

    public static ShieldMaceFeature feature;
    public static PearlInterceptor   pearlInterceptor;

    private static final int GLFW_KEY_UNKNOWN       = -1;
    private static final int GLFW_KEY_RIGHT_SHIFT   = 344;
    private static final int GLFW_KEY_RIGHT_CONTROL = 345;
    private static final int GLFW_KEY_RIGHT_ALT     = 346;

    private static KeyBinding kb(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldmacemod." + id,
                InputUtil.Type.KEYSYM,
                code,
                KeyBinding.Category.MISC));
    }

    @Override
    public void onInitializeClient() {
        openGuiKey              = kb("openGui",              GLFW_KEY_RIGHT_SHIFT);
        toggleComboKey          = kb("toggleCombo",          GLFW_KEY_UNKNOWN);
        toggleBreachSwapKey     = kb("toggleBreachSwap",     GLFW_KEY_RIGHT_CONTROL);
        toggleMaceSpamKey       = kb("toggleMaceSpam",       GLFW_KEY_RIGHT_ALT);
        togglePearlInterceptKey = kb("togglePearlIntercept", GLFW_KEY_UNKNOWN);
        toggleSilentAimKey      = kb("toggleSilentAim",      GLFW_KEY_UNKNOWN);
        toggleHeightSmashKey    = kb("toggleHeightSmash",    GLFW_KEY_UNKNOWN);
        toggleHitboxExpandKey   = kb("toggleHitboxExpand",   GLFW_KEY_UNKNOWN);
        toggleAutoTotemKey      = kb("toggleAutoTotem",      GLFW_KEY_UNKNOWN);
        toggleNoFallKey         = kb("toggleNoFall",         GLFW_KEY_UNKNOWN);
        toggleKillAuraKey       = kb("toggleKillAura",       GLFW_KEY_UNKNOWN);
        toggleFlightKey         = kb("toggleFlight",         GLFW_KEY_UNKNOWN);
        toggleBlinkKey          = kb("toggleBlink",          GLFW_KEY_UNKNOWN);

        toggleSpeedKey          = kb("toggleSpeed",          GLFW_KEY_UNKNOWN);
        toggleSprintKey         = kb("toggleSprint",         GLFW_KEY_UNKNOWN);
        toggleStepKey           = kb("toggleStep",           GLFW_KEY_UNKNOWN);
        toggleLongJumpKey       = kb("toggleLongJump",       GLFW_KEY_UNKNOWN);
        toggleJesusKey          = kb("toggleJesus",          GLFW_KEY_UNKNOWN);
        toggleSpiderKey         = kb("toggleSpider",         GLFW_KEY_UNKNOWN);
        toggleGlideKey          = kb("toggleGlide",          GLFW_KEY_UNKNOWN);

        toggleTriggerBotKey     = kb("toggleTriggerBot",     GLFW_KEY_UNKNOWN);
        toggleAutoClickerKey    = kb("toggleAutoClicker",    GLFW_KEY_UNKNOWN);
        toggleCriticalsKey      = kb("toggleCriticals",      GLFW_KEY_UNKNOWN);
        toggleReachKey          = kb("toggleReach",          GLFW_KEY_UNKNOWN);
        toggleAntiKnockbackKey  = kb("toggleAntiKnockback",  GLFW_KEY_UNKNOWN);

        toggleAutoArmorKey      = kb("toggleAutoArmor",      GLFW_KEY_UNKNOWN);
        toggleFastUseKey        = kb("toggleFastUse",        GLFW_KEY_UNKNOWN);
        toggleInventoryMoveKey  = kb("toggleInventoryMove",  GLFW_KEY_UNKNOWN);
        toggleChestStealerKey   = kb("toggleChestStealer",   GLFW_KEY_UNKNOWN);

        toggleXrayKey           = kb("toggleXray",           GLFW_KEY_UNKNOWN);
        toggleEspKey            = kb("toggleEsp",            GLFW_KEY_UNKNOWN);
        toggleTracersKey        = kb("toggleTracers",        GLFW_KEY_UNKNOWN);
        toggleFullbrightKey     = kb("toggleFullbright",     GLFW_KEY_UNKNOWN);
        toggleNameTagsKey       = kb("toggleNameTags",       GLFW_KEY_UNKNOWN);

        feature          = new ShieldMaceFeature();
        pearlInterceptor = new PearlInterceptor();

        // Render hooks (ESP / Tracers / X-Ray ore highlight / Name Tags)
        RenderHooks.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) client.setScreen(new ShieldMaceGui());
            while (toggleComboKey.wasPressed())          feature.toggleCombo(client);
            while (toggleBreachSwapKey.wasPressed())     feature.toggleBreachSwap(client);
            while (toggleMaceSpamKey.wasPressed())       feature.toggleMaceSpam(client);
            while (togglePearlInterceptKey.wasPressed()) pearlInterceptor.toggle(client);
            while (toggleSilentAimKey.wasPressed())      feature.toggleSilentAim(client);
            while (toggleHeightSmashKey.wasPressed())    feature.toggleHeightSmash(client);
            while (toggleHitboxExpandKey.wasPressed())   feature.toggleHitboxExpand(client);
            while (toggleAutoTotemKey.wasPressed())      feature.toggleAutoTotem(client);
            while (toggleNoFallKey.wasPressed())         feature.toggleNoFall(client);
            while (toggleKillAuraKey.wasPressed())       feature.toggleKillAura(client);
            while (toggleFlightKey.wasPressed())         feature.toggleFlight(client);
            while (toggleBlinkKey.wasPressed())          feature.toggleBlink(client);

            while (toggleSpeedKey.wasPressed())          feature.toggleSimple(client, "Speed",        () -> ShieldMaceSettings.INSTANCE.speedEnabled        = !ShieldMaceSettings.INSTANCE.speedEnabled,        () -> ShieldMaceSettings.INSTANCE.speedEnabled);
            while (toggleSprintKey.wasPressed())         feature.toggleSimple(client, "Sprint",       () -> ShieldMaceSettings.INSTANCE.sprintEnabled       = !ShieldMaceSettings.INSTANCE.sprintEnabled,       () -> ShieldMaceSettings.INSTANCE.sprintEnabled);
            while (toggleStepKey.wasPressed())           feature.toggleSimple(client, "Step",         () -> ShieldMaceSettings.INSTANCE.stepEnabled         = !ShieldMaceSettings.INSTANCE.stepEnabled,         () -> ShieldMaceSettings.INSTANCE.stepEnabled);
            while (toggleLongJumpKey.wasPressed())       feature.toggleSimple(client, "Long Jump",    () -> ShieldMaceSettings.INSTANCE.longJumpEnabled     = !ShieldMaceSettings.INSTANCE.longJumpEnabled,     () -> ShieldMaceSettings.INSTANCE.longJumpEnabled);
            while (toggleJesusKey.wasPressed())          feature.toggleSimple(client, "Jesus",        () -> ShieldMaceSettings.INSTANCE.jesusEnabled        = !ShieldMaceSettings.INSTANCE.jesusEnabled,        () -> ShieldMaceSettings.INSTANCE.jesusEnabled);
            while (toggleSpiderKey.wasPressed())         feature.toggleSimple(client, "Spider",       () -> ShieldMaceSettings.INSTANCE.spiderEnabled       = !ShieldMaceSettings.INSTANCE.spiderEnabled,       () -> ShieldMaceSettings.INSTANCE.spiderEnabled);
            while (toggleGlideKey.wasPressed())          feature.toggleSimple(client, "Glide",        () -> ShieldMaceSettings.INSTANCE.glideEnabled        = !ShieldMaceSettings.INSTANCE.glideEnabled,        () -> ShieldMaceSettings.INSTANCE.glideEnabled);

            while (toggleTriggerBotKey.wasPressed())     feature.toggleSimple(client, "TriggerBot",   () -> ShieldMaceSettings.INSTANCE.triggerBotEnabled   = !ShieldMaceSettings.INSTANCE.triggerBotEnabled,   () -> ShieldMaceSettings.INSTANCE.triggerBotEnabled);
            while (toggleAutoClickerKey.wasPressed())    feature.toggleSimple(client, "AutoClicker",  () -> ShieldMaceSettings.INSTANCE.autoClickerEnabled  = !ShieldMaceSettings.INSTANCE.autoClickerEnabled,  () -> ShieldMaceSettings.INSTANCE.autoClickerEnabled);
            while (toggleCriticalsKey.wasPressed())      feature.toggleSimple(client, "Criticals",    () -> ShieldMaceSettings.INSTANCE.criticalsEnabled    = !ShieldMaceSettings.INSTANCE.criticalsEnabled,    () -> ShieldMaceSettings.INSTANCE.criticalsEnabled);
            while (toggleReachKey.wasPressed())          feature.toggleSimple(client, "Reach",        () -> ShieldMaceSettings.INSTANCE.reachEnabled        = !ShieldMaceSettings.INSTANCE.reachEnabled,        () -> ShieldMaceSettings.INSTANCE.reachEnabled);
            while (toggleAntiKnockbackKey.wasPressed())  feature.toggleSimple(client, "Anti-Knockback",() -> ShieldMaceSettings.INSTANCE.antiKnockbackEnabled = !ShieldMaceSettings.INSTANCE.antiKnockbackEnabled, () -> ShieldMaceSettings.INSTANCE.antiKnockbackEnabled);

            while (toggleAutoArmorKey.wasPressed())      feature.toggleSimple(client, "AutoArmor",    () -> ShieldMaceSettings.INSTANCE.autoArmorEnabled    = !ShieldMaceSettings.INSTANCE.autoArmorEnabled,    () -> ShieldMaceSettings.INSTANCE.autoArmorEnabled);
            while (toggleFastUseKey.wasPressed())        feature.toggleSimple(client, "FastUse",      () -> ShieldMaceSettings.INSTANCE.fastUseEnabled      = !ShieldMaceSettings.INSTANCE.fastUseEnabled,      () -> ShieldMaceSettings.INSTANCE.fastUseEnabled);
            while (toggleInventoryMoveKey.wasPressed())  feature.toggleSimple(client, "InventoryMove",() -> ShieldMaceSettings.INSTANCE.inventoryMoveEnabled = !ShieldMaceSettings.INSTANCE.inventoryMoveEnabled, () -> ShieldMaceSettings.INSTANCE.inventoryMoveEnabled);
            while (toggleChestStealerKey.wasPressed())   feature.toggleSimple(client, "ChestStealer", () -> ShieldMaceSettings.INSTANCE.chestStealerEnabled = !ShieldMaceSettings.INSTANCE.chestStealerEnabled, () -> ShieldMaceSettings.INSTANCE.chestStealerEnabled);

            while (toggleXrayKey.wasPressed())           feature.toggleSimple(client, "X-Ray",        () -> ShieldMaceSettings.INSTANCE.xrayEnabled         = !ShieldMaceSettings.INSTANCE.xrayEnabled,         () -> ShieldMaceSettings.INSTANCE.xrayEnabled);
            while (toggleEspKey.wasPressed())            feature.toggleSimple(client, "ESP",          () -> ShieldMaceSettings.INSTANCE.espEnabled          = !ShieldMaceSettings.INSTANCE.espEnabled,          () -> ShieldMaceSettings.INSTANCE.espEnabled);
            while (toggleTracersKey.wasPressed())        feature.toggleSimple(client, "Tracers",      () -> ShieldMaceSettings.INSTANCE.tracersEnabled      = !ShieldMaceSettings.INSTANCE.tracersEnabled,      () -> ShieldMaceSettings.INSTANCE.tracersEnabled);
            while (toggleFullbrightKey.wasPressed())     feature.toggleSimple(client, "Fullbright",   () -> ShieldMaceSettings.INSTANCE.fullbrightEnabled   = !ShieldMaceSettings.INSTANCE.fullbrightEnabled,   () -> ShieldMaceSettings.INSTANCE.fullbrightEnabled);
            while (toggleNameTagsKey.wasPressed())       feature.toggleSimple(client, "NameTags",     () -> ShieldMaceSettings.INSTANCE.nameTagsEnabled     = !ShieldMaceSettings.INSTANCE.nameTagsEnabled,     () -> ShieldMaceSettings.INSTANCE.nameTagsEnabled);

            feature.tick(client);
            pearlInterceptor.tick(client);
        });
    }
}
