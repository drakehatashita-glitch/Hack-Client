package com.shieldmacemod;

public final class ShieldMaceSettings {
    public static final ShieldMaceSettings INSTANCE = new ShieldMaceSettings();

    // Feature 1 — Shield Mace Combo (axe + mace auto-combo & shield break)
    public boolean comboEnabled = false;
    public int comboSwapDelayTicks = 3;     // 1..10
    public int comboCooldownTicks  = 10;    // 1..40

    // Feature 2 — Breach Mace Swap
    public boolean breachSwapEnabled = false;
    public int breachSwapDelayTicks    = 3;  // 1..10
    public int breachSwapCooldownTicks = 10; // 1..40

    // Feature 3 — Mace Spam
    public boolean maceSpamEnabled = false;
    public int maceSpamClicksPerTick = 25;   // 1..100
    /** Sub-toggle: while on, replaces 25-cps spam with a click rate computed from
     *  the player's current fall speed, aiming for fd ≈ 3 per smash so each hit
     *  is in the mace's max-bonus zone (4 dmg/block). Used to drain a U3 shield
     *  while falling. */
    public boolean maceSpamSmartFallClick = false;

    // Feature 5 — Silent Aim (soft aim assist; only activates when crosshair is already close)
    public boolean silentAimEnabled = false;
    /** Max angle in degrees between crosshair and target before assist kicks in. 1..30 */
    public int silentAimMaxAngleDegrees = 8;
    /** % of the angular distance to close per tick. 1..100 */
    public int silentAimStrengthPct = 25;
    /** Max range in blocks to consider a target. 1..30 */
    public int silentAimRangeBlocks = 6;

    // Feature 4 — Pearl Wind-Charge Intercept
    public boolean pearlInterceptEnabled = false;
    /** 1..30, displayed as "0.1 .. 3.0 blocks" — how close WC trajectory must come to the pearl. */
    public int pearlInterceptToleranceTenths = 15;
    /** 10..80 ticks — how far ahead to predict the pearl when searching for an intercept point. */
    public int pearlInterceptLookahead = 40;

    // Feature 7 — Hitbox Expander (client-side visual + targeting box expansion on other players)
    /** When ON, every other player's targeting margin (used for client raycast
     *  hit-tests) is enlarged by `hitboxExpandTenths / 10` blocks, so attacks
     *  and crosshair targeting register over a wider area around them. The
     *  local player is unaffected. Collision boxes are not changed, so this
     *  does not push players around. */
    public boolean hitboxExpandEnabled = false;
    /** 1..50 — expansion in tenths of a block. Displayed as 0.1 .. 5.0 blocks. */
    public int hitboxExpandTenths = 10;

    // Feature 8 — Auto Totem (keep a totem in the offhand whenever one is in the inventory)
    /** When ON, every `autoTotemDelayTicks` ticks the offhand is checked. If
     *  it doesn't already hold a Totem of Undying and one exists anywhere in
     *  the main inventory or hotbar, the client sends a swap click that puts
     *  the totem into the offhand (replacing whatever was there). */
    public boolean autoTotemEnabled = false;
    /** 1..40 — minimum ticks between swap attempts. Keeps us from hammering
     *  the server with click-slot packets if a totem swap fails for any reason. */
    public int autoTotemDelayTicks = 5;

    // Feature 9 — No Fall (spoof onGround=true on outgoing position packets while falling)
    /** When ON, the client lies to the server about being on the ground for
     *  every position packet sent while the local player's tracked fall
     *  distance is at least 2 blocks, preventing the server from ever
     *  accumulating enough fall-distance to deal damage on landing. */
    public boolean noFallEnabled = false;

    // Feature 10 — Kill Aura (auto-attack the closest matching entity in range)
    /** When ON, every `killAuraDelayTicks` ticks the closest valid target
     *  within `killAuraRangeBlocks` is attacked. The three target-class
     *  booleans below decide what counts as a valid target. */
    public boolean killAuraEnabled = false;
    /** 1..8 — attack range in blocks. Vanilla reach is ~3, so values past
     *  4 are typically rejected by anti-cheats. */
    public int killAuraRangeBlocks = 4;
    /** 1..20 — minimum ticks between attacks. 10 = 2 attacks/sec. */
    public int killAuraDelayTicks = 10;
    /** Attack other players (excluding spectators / creative). */
    public boolean killAuraTargetPlayers = true;
    /** Attack hostile mobs (zombies, skeletons, creepers, etc). */
    public boolean killAuraTargetHostile = true;
    /** Attack passive mobs (cows, sheep, villagers, etc). */
    public boolean killAuraTargetPassive = false;

    // Feature 11 — Flight (creative-style client-side fly)
    /** When ON, allowFlying is forced true every tick so the player can
     *  double-tap space to take off (vanilla-creative behaviour). NOTE:
     *  this is purely client-side abilities — survival servers with
     *  movement anti-cheat will rubberband you back to the ground. */
    public boolean flightEnabled = false;
    /** 1..50 — flight speed in hundredths. 5 ≈ vanilla creative speed,
     *  50 ≈ ~10× vanilla. */
    public int flightSpeedTenths = 10;

    // Feature 6 — Height Smash (fake fall-distance for max mace smash damage)
    /** When ON, every left-click attack on a living entity while holding a mace
     *  is preceded by a burst of PlayerMoveC2SPacket packets that fake a
     *  large drop in the player's Y position. This causes the server to
     *  accumulate fall-distance up to ~world-height limit, so the mace's
     *  smash bonus is maxed out for that hit. The player is then sent
     *  back up so the server's reach check still passes. */
    public boolean heightSmashEnabled = false;
    /** 1..40 — number of fake drop packets sent. Total faked fall =
     *  heightSmashPackets × heightSmashDropPerPacket blocks. */
    public int heightSmashPackets = 36;
    /** 1..9 — Y drop per fake packet. Must stay under 10 to avoid the
     *  vanilla server's per-packet movement-too-fast check. */
    public int heightSmashDropPerPacket = 9;

    private ShieldMaceSettings() {}
}
