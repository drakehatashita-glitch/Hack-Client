package com.shieldmacemod;

public final class ShieldMaceSettings {
    public static final ShieldMaceSettings INSTANCE = new ShieldMaceSettings();

    // ── Existing features ───────────────────────────────────────────────
    public boolean comboEnabled = false;
    public int comboSwapDelayTicks = 3;
    public int comboCooldownTicks  = 10;

    public boolean breachSwapEnabled = false;
    public int breachSwapDelayTicks    = 3;
    public int breachSwapCooldownTicks = 10;

    public boolean maceSpamEnabled = false;
    public int maceSpamClicksPerTick = 25;
    public boolean maceSpamSmartFallClick = false;

    public boolean silentAimEnabled = false;
    public int silentAimMaxAngleDegrees = 8;
    public int silentAimStrengthPct = 25;
    public int silentAimRangeBlocks = 6;

    public boolean pearlInterceptEnabled = false;
    public int pearlInterceptToleranceTenths = 15;
    public int pearlInterceptLookahead = 40;

    public boolean hitboxExpandEnabled = false;
    public int hitboxExpandTenths = 10;

    public boolean autoTotemEnabled = false;
    public int autoTotemDelayTicks = 5;

    public boolean noFallEnabled = false;

    public boolean killAuraEnabled = false;
    public int killAuraRangeBlocks = 4;
    public int killAuraDelayTicks = 10;
    public boolean killAuraTargetPlayers = true;
    public boolean killAuraTargetHostile = true;
    public boolean killAuraTargetPassive = false;
    public boolean killAuraTargetAll = false;

    public boolean blinkEnabled = false;

    public boolean flightEnabled = false;
    public int flightSpeedTenths = 10;

    public boolean heightSmashEnabled = false;
    public int heightSmashPackets = 36;
    public int heightSmashDropPerPacket = 9;

    // ── Movement ────────────────────────────────────────────────────────
    /** Horizontal velocity multiplier x10. Range 11..50 ⇒ 1.1..5.0×. */
    public boolean speedEnabled = false;
    public int speedMultiplierTenths = 15;

    /** Forces sprint flag every tick whenever the player is moving forward. */
    public boolean sprintEnabled = false;

    /** Auto step-up: raises the player's step-height attribute. 1..30 ⇒ 0.1..3.0 blocks. */
    public boolean stepEnabled = false;
    public int stepHeightTenths = 10;

    /** Multiply horizontal velocity by N when jumping. 11..40 ⇒ 1.1..4.0×. */
    public boolean longJumpEnabled = false;
    public int longJumpMultiplierTenths = 20;

    /** Walk on liquids: clamp downward velocity when over water/lava. */
    public boolean jesusEnabled = false;

    /** Spider climb: set upward velocity on horizontal collision. 1..40 ⇒ 0.01..0.40 blocks/tick. */
    public boolean spiderEnabled = false;
    public int spiderClimbTenths = 20;

    /** Glide: clamp downward velocity to slow descent. 1..50 ⇒ 0.01..0.50 blocks/tick. */
    public boolean glideEnabled = false;
    public int glideFallSpeedTenths = 8;

    // ── Combat ──────────────────────────────────────────────────────────
    /** Auto-attack the entity under the crosshair when one is targeted. */
    public boolean triggerBotEnabled = false;
    public int triggerBotDelayTicks = 4;

    /** Generic auto-clicker that attacks while the attack key is held. */
    public boolean autoClickerEnabled = false;
    public int autoClickerCps = 12;

    /** Force a critical hit on every melee attack (sends fake fall packets). */
    public boolean criticalsEnabled = false;

    /** Increase melee attack range via the entity-interaction-range attribute. 30..80 ⇒ 3.0..8.0 blocks. */
    public boolean reachEnabled = false;
    public int reachBlocksTenths = 50;

    /** Cancel server-sent velocity packets that would push the local player. */
    public boolean antiKnockbackEnabled = false;

    // ── Player Assistance ───────────────────────────────────────────────
    /** Equip the strongest armor pieces from inventory automatically. */
    public boolean autoArmorEnabled = false;
    public int autoArmorDelayTicks = 10;

    /** Removes the use-item delay so food, potions etc. activate instantly. */
    public boolean fastUseEnabled = false;

    /** Allow movement keys to function while a screen is open. */
    public boolean inventoryMoveEnabled = false;

    /** Auto-shift-click items out of any opened container (chest, shulker, …). */
    public boolean chestStealerEnabled = false;
    public int chestStealerDelayTicks = 2;

    // ── Render / Visual ─────────────────────────────────────────────────
    /** Highlight ore blocks (visible through walls). */
    public boolean xrayEnabled = false;
    public int xrayRangeBlocks = 32;

    /** Outline boxes around entities, visible through walls. */
    public boolean espEnabled = false;
    public boolean espShowPlayers = true;
    public boolean espShowHostile = true;
    public boolean espShowPassive = false;
    public boolean espShowItems = false;

    /** Lines from the camera to entities. */
    public boolean tracersEnabled = false;
    public boolean tracersShowPlayers = true;
    public boolean tracersShowHostile = false;
    public boolean tracersShowPassive = false;

    /** Force gamma to the maximum so dark areas are fully lit. */
    public boolean fullbrightEnabled = false;

    /** Render player nametags through walls. */
    public boolean nameTagsEnabled = false;
    public int nameTagsRangeBlocks = 64;

    private ShieldMaceSettings() {}
}
