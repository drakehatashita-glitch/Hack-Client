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

    private ShieldMaceSettings() {}
}
