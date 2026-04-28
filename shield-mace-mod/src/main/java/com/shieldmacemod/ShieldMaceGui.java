package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ShieldMaceGui extends Screen {

    private static final int CARD_WIDTH         = 320;
    private static final int CARD_HEADER_HEIGHT = 28;
    private static final int CARD_INNER_PADDING = 4;
    private static final int CARD_GAP           = 6;
    private static final int WIDGET_HEIGHT      = 20;
    private static final int TOP_BAR_HEIGHT     = 32;
    private static final int CONTENT_TOP        = TOP_BAR_HEIGHT + 8;

    private static final int COLOR_TITLE          = 0xFFFFFFFF;
    private static final int COLOR_CARD_BG        = 0xC0202020;
    private static final int COLOR_CARD_BG_HOVER  = 0xC0303040;
    private static final int COLOR_CARD_BORDER    = 0xFF505060;
    private static final int COLOR_LABEL          = 0xFFE0E0E0;
    private static final int COLOR_HINT           = 0xFF9090A0;
    private static final int COLOR_PROMPT         = 0xFFFFD040;
    private static final int COLOR_CATEGORY       = 0xFF80B0FF;

    // GLFW key codes (inlined)
    private static final int GLFW_KEY_UNKNOWN = -1;
    private static final int GLFW_KEY_ESCAPE  = 256;

    /** All feature cards (full list, never filtered). */
    private final List<FeatureCard> allCards = new ArrayList<>();
    /** Cards currently visible after applying the search filter. */
    private final List<FeatureCard> visibleCards = new ArrayList<>();

    /** Index into visibleCards[] of the feature whose keybind we're currently capturing, or -1. */
    private int pendingRebind = -1;

    /** Vertical scroll offset (positive = scrolled down). */
    private int scrollY = 0;
    /** Total content height of the visible cards (computed during layout). */
    private int contentHeight = 0;

    private TextFieldWidget searchBox;
    private String searchQuery = "";

    public ShieldMaceGui() {
        super(Text.literal("Shield Mace Mod"));
    }

    @Override
    protected void init() {
        if (allCards.isEmpty()) {
            buildCardList();
        }
        // Search box
        int boxW = 200, boxH = WIDGET_HEIGHT;
        int boxX = (this.width - boxW) / 2;
        int boxY = 6;
        searchBox = new TextFieldWidget(this.textRenderer, boxX, boxY, boxW, boxH,
                Text.literal("Search…"));
        searchBox.setMaxLength(40);
        searchBox.setText(searchQuery);
        searchBox.setChangedListener(s -> {
            searchQuery = s == null ? "" : s;
            scrollY = 0;
            layoutAndAddWidgets();
        });
        addDrawableChild(searchBox);

        layoutAndAddWidgets();
    }

    private void buildCardList() {
        // ── Combat & original utility ─────────────────────────────────────────
        allCards.add(card("Auto Stun Slam", "Combat",
                () -> ShieldMaceMod.toggleComboKey,
                () -> ShieldMaceSettings.INSTANCE.comboEnabled,
                v -> ShieldMaceSettings.INSTANCE.comboEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Swap Delay (ticks)", 1, 10,
                            () -> ShieldMaceSettings.INSTANCE.comboSwapDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.comboSwapDelayTicks = v),
                    new SettingSpec("Cooldown (ticks)", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.comboCooldownTicks,
                            v -> ShieldMaceSettings.INSTANCE.comboCooldownTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Breach Swap", "Combat",
                () -> ShieldMaceMod.toggleBreachSwapKey,
                () -> ShieldMaceSettings.INSTANCE.breachSwapEnabled,
                v -> ShieldMaceSettings.INSTANCE.breachSwapEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Swap Delay (ticks)", 1, 10,
                            () -> ShieldMaceSettings.INSTANCE.breachSwapDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.breachSwapDelayTicks = v),
                    new SettingSpec("Cooldown (ticks)", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.breachSwapCooldownTicks,
                            v -> ShieldMaceSettings.INSTANCE.breachSwapCooldownTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Shield Breaker", "Combat",
                () -> ShieldMaceMod.toggleMaceSpamKey,
                () -> ShieldMaceSettings.INSTANCE.maceSpamEnabled,
                v -> ShieldMaceSettings.INSTANCE.maceSpamEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Clicks per tick", 1, 100,
                            () -> ShieldMaceSettings.INSTANCE.maceSpamClicksPerTick,
                            v -> ShieldMaceSettings.INSTANCE.maceSpamClicksPerTick = v)
                }, new BoolToggleSpec[]{
                    new BoolToggleSpec("Smart Fall Click",
                            () -> ShieldMaceSettings.INSTANCE.maceSpamSmartFallClick,
                            v -> ShieldMaceSettings.INSTANCE.maceSpamSmartFallClick = v)
                }));
        allCards.add(card("Silent Aim", "Combat",
                () -> ShieldMaceMod.toggleSilentAimKey,
                () -> ShieldMaceSettings.INSTANCE.silentAimEnabled,
                v -> ShieldMaceSettings.INSTANCE.silentAimEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Max angle (deg)", 1, 30,
                            () -> ShieldMaceSettings.INSTANCE.silentAimMaxAngleDegrees,
                            v -> ShieldMaceSettings.INSTANCE.silentAimMaxAngleDegrees = v),
                    new SettingSpec("Strength (%)", 1, 100,
                            () -> ShieldMaceSettings.INSTANCE.silentAimStrengthPct,
                            v -> ShieldMaceSettings.INSTANCE.silentAimStrengthPct = v),
                    new SettingSpec("Range (blocks)", 1, 30,
                            () -> ShieldMaceSettings.INSTANCE.silentAimRangeBlocks,
                            v -> ShieldMaceSettings.INSTANCE.silentAimRangeBlocks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Hitboxes", "Combat",
                () -> ShieldMaceMod.toggleHitboxExpandKey,
                () -> ShieldMaceSettings.INSTANCE.hitboxExpandEnabled,
                v -> ShieldMaceSettings.INSTANCE.hitboxExpandEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Expand x0.1 blocks", 1, 50,
                            () -> ShieldMaceSettings.INSTANCE.hitboxExpandTenths,
                            v -> ShieldMaceSettings.INSTANCE.hitboxExpandTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Mace Kill", "Combat",
                () -> ShieldMaceMod.toggleHeightSmashKey,
                () -> ShieldMaceSettings.INSTANCE.heightSmashEnabled,
                v -> ShieldMaceSettings.INSTANCE.heightSmashEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Drop packets", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.heightSmashPackets,
                            v -> ShieldMaceSettings.INSTANCE.heightSmashPackets = v),
                    new SettingSpec("Y per packet (blocks)", 1, 9,
                            () -> ShieldMaceSettings.INSTANCE.heightSmashDropPerPacket,
                            v -> ShieldMaceSettings.INSTANCE.heightSmashDropPerPacket = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Kill Aura", "Combat",
                () -> ShieldMaceMod.toggleKillAuraKey,
                () -> ShieldMaceSettings.INSTANCE.killAuraEnabled,
                v -> ShieldMaceSettings.INSTANCE.killAuraEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Range (blocks)", 1, 8,
                            () -> ShieldMaceSettings.INSTANCE.killAuraRangeBlocks,
                            v -> ShieldMaceSettings.INSTANCE.killAuraRangeBlocks = v),
                    new SettingSpec("Attack delay (ticks)", 1, 20,
                            () -> ShieldMaceSettings.INSTANCE.killAuraDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.killAuraDelayTicks = v)
                }, new BoolToggleSpec[]{
                    new BoolToggleSpec("Target Players",
                            () -> ShieldMaceSettings.INSTANCE.killAuraTargetPlayers,
                            v -> ShieldMaceSettings.INSTANCE.killAuraTargetPlayers = v),
                    new BoolToggleSpec("Target Hostile Mobs",
                            () -> ShieldMaceSettings.INSTANCE.killAuraTargetHostile,
                            v -> ShieldMaceSettings.INSTANCE.killAuraTargetHostile = v),
                    new BoolToggleSpec("Target Passive Mobs",
                            () -> ShieldMaceSettings.INSTANCE.killAuraTargetPassive,
                            v -> ShieldMaceSettings.INSTANCE.killAuraTargetPassive = v),
                    new BoolToggleSpec("Multi-target",
                            () -> ShieldMaceSettings.INSTANCE.killAuraTargetAll,
                            v -> ShieldMaceSettings.INSTANCE.killAuraTargetAll = v)
                }));
        allCards.add(card("TriggerBot", "Combat",
                () -> ShieldMaceMod.toggleTriggerBotKey,
                () -> ShieldMaceSettings.INSTANCE.triggerBotEnabled,
                v -> ShieldMaceSettings.INSTANCE.triggerBotEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Delay (ticks)", 1, 20,
                            () -> ShieldMaceSettings.INSTANCE.triggerBotDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.triggerBotDelayTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("AutoClicker", "Combat",
                () -> ShieldMaceMod.toggleAutoClickerKey,
                () -> ShieldMaceSettings.INSTANCE.autoClickerEnabled,
                v -> ShieldMaceSettings.INSTANCE.autoClickerEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("CPS", 1, 30,
                            () -> ShieldMaceSettings.INSTANCE.autoClickerCps,
                            v -> ShieldMaceSettings.INSTANCE.autoClickerCps = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Criticals", "Combat",
                () -> ShieldMaceMod.toggleCriticalsKey,
                () -> ShieldMaceSettings.INSTANCE.criticalsEnabled,
                v -> ShieldMaceSettings.INSTANCE.criticalsEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("Reach", "Combat",
                () -> ShieldMaceMod.toggleReachKey,
                () -> ShieldMaceSettings.INSTANCE.reachEnabled,
                v -> ShieldMaceSettings.INSTANCE.reachEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Range x0.1 blocks", 30, 80,
                            () -> ShieldMaceSettings.INSTANCE.reachBlocksTenths,
                            v -> ShieldMaceSettings.INSTANCE.reachBlocksTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Anti-Knockback", "Combat",
                () -> ShieldMaceMod.toggleAntiKnockbackKey,
                () -> ShieldMaceSettings.INSTANCE.antiKnockbackEnabled,
                v -> ShieldMaceSettings.INSTANCE.antiKnockbackEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));

        // ── Movement ──────────────────────────────────────────────────────────
        allCards.add(card("Flight", "Movement",
                () -> ShieldMaceMod.toggleFlightKey,
                () -> ShieldMaceSettings.INSTANCE.flightEnabled,
                v -> ShieldMaceSettings.INSTANCE.flightEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Speed x0.01", 1, 50,
                            () -> ShieldMaceSettings.INSTANCE.flightSpeedTenths,
                            v -> ShieldMaceSettings.INSTANCE.flightSpeedTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Speed", "Movement",
                () -> ShieldMaceMod.toggleSpeedKey,
                () -> ShieldMaceSettings.INSTANCE.speedEnabled,
                v -> ShieldMaceSettings.INSTANCE.speedEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Multiplier x0.1", 11, 50,
                            () -> ShieldMaceSettings.INSTANCE.speedMultiplierTenths,
                            v -> ShieldMaceSettings.INSTANCE.speedMultiplierTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Sprint", "Movement",
                () -> ShieldMaceMod.toggleSprintKey,
                () -> ShieldMaceSettings.INSTANCE.sprintEnabled,
                v -> ShieldMaceSettings.INSTANCE.sprintEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("Step", "Movement",
                () -> ShieldMaceMod.toggleStepKey,
                () -> ShieldMaceSettings.INSTANCE.stepEnabled,
                v -> ShieldMaceSettings.INSTANCE.stepEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Height x0.1 blocks", 1, 30,
                            () -> ShieldMaceSettings.INSTANCE.stepHeightTenths,
                            v -> ShieldMaceSettings.INSTANCE.stepHeightTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Long Jump", "Movement",
                () -> ShieldMaceMod.toggleLongJumpKey,
                () -> ShieldMaceSettings.INSTANCE.longJumpEnabled,
                v -> ShieldMaceSettings.INSTANCE.longJumpEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Multiplier x0.1", 11, 40,
                            () -> ShieldMaceSettings.INSTANCE.longJumpMultiplierTenths,
                            v -> ShieldMaceSettings.INSTANCE.longJumpMultiplierTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("No Fall", "Movement",
                () -> ShieldMaceMod.toggleNoFallKey,
                () -> ShieldMaceSettings.INSTANCE.noFallEnabled,
                v -> ShieldMaceSettings.INSTANCE.noFallEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("Jesus", "Movement",
                () -> ShieldMaceMod.toggleJesusKey,
                () -> ShieldMaceSettings.INSTANCE.jesusEnabled,
                v -> ShieldMaceSettings.INSTANCE.jesusEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("Spider", "Movement",
                () -> ShieldMaceMod.toggleSpiderKey,
                () -> ShieldMaceSettings.INSTANCE.spiderEnabled,
                v -> ShieldMaceSettings.INSTANCE.spiderEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Climb x0.01 b/tick", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.spiderClimbTenths,
                            v -> ShieldMaceSettings.INSTANCE.spiderClimbTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Glide", "Movement",
                () -> ShieldMaceMod.toggleGlideKey,
                () -> ShieldMaceSettings.INSTANCE.glideEnabled,
                v -> ShieldMaceSettings.INSTANCE.glideEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Fall speed x0.01 b/tick", 1, 50,
                            () -> ShieldMaceSettings.INSTANCE.glideFallSpeedTenths,
                            v -> ShieldMaceSettings.INSTANCE.glideFallSpeedTenths = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Blink", "Movement",
                () -> ShieldMaceMod.toggleBlinkKey,
                () -> ShieldMaceSettings.INSTANCE.blinkEnabled,
                enabled -> {
                    if (ShieldMaceMod.feature != null
                            && ShieldMaceSettings.INSTANCE.blinkEnabled != enabled) {
                        ShieldMaceMod.feature.toggleBlink(MinecraftClient.getInstance());
                    } else {
                        ShieldMaceSettings.INSTANCE.blinkEnabled = enabled;
                    }
                },
                new SettingSpec[0], new BoolToggleSpec[0]));

        // ── Player Assist ─────────────────────────────────────────────────────
        allCards.add(card("Auto Totem", "Assist",
                () -> ShieldMaceMod.toggleAutoTotemKey,
                () -> ShieldMaceSettings.INSTANCE.autoTotemEnabled,
                v -> ShieldMaceSettings.INSTANCE.autoTotemEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Re-check delay (ticks)", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.autoTotemDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.autoTotemDelayTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("AutoArmor", "Assist",
                () -> ShieldMaceMod.toggleAutoArmorKey,
                () -> ShieldMaceSettings.INSTANCE.autoArmorEnabled,
                v -> ShieldMaceSettings.INSTANCE.autoArmorEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Delay (ticks)", 1, 40,
                            () -> ShieldMaceSettings.INSTANCE.autoArmorDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.autoArmorDelayTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("FastUse", "Assist",
                () -> ShieldMaceMod.toggleFastUseKey,
                () -> ShieldMaceSettings.INSTANCE.fastUseEnabled,
                v -> ShieldMaceSettings.INSTANCE.fastUseEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("InventoryMove", "Assist",
                () -> ShieldMaceMod.toggleInventoryMoveKey,
                () -> ShieldMaceSettings.INSTANCE.inventoryMoveEnabled,
                v -> ShieldMaceSettings.INSTANCE.inventoryMoveEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("ChestStealer", "Assist",
                () -> ShieldMaceMod.toggleChestStealerKey,
                () -> ShieldMaceSettings.INSTANCE.chestStealerEnabled,
                v -> ShieldMaceSettings.INSTANCE.chestStealerEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Delay (ticks)", 1, 20,
                            () -> ShieldMaceSettings.INSTANCE.chestStealerDelayTicks,
                            v -> ShieldMaceSettings.INSTANCE.chestStealerDelayTicks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("Pearl Catch", "Assist",
                () -> ShieldMaceMod.togglePearlInterceptKey,
                () -> ShieldMaceSettings.INSTANCE.pearlInterceptEnabled,
                v -> ShieldMaceSettings.INSTANCE.pearlInterceptEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Tolerance x0.1 blocks", 1, 30,
                            () -> ShieldMaceSettings.INSTANCE.pearlInterceptToleranceTenths,
                            v -> ShieldMaceSettings.INSTANCE.pearlInterceptToleranceTenths = v),
                    new SettingSpec("Lookahead (ticks)", 10, 80,
                            () -> ShieldMaceSettings.INSTANCE.pearlInterceptLookahead,
                            v -> ShieldMaceSettings.INSTANCE.pearlInterceptLookahead = v)
                }, new BoolToggleSpec[0]));

        // ── Render ────────────────────────────────────────────────────────────
        allCards.add(card("X-Ray", "Render",
                () -> ShieldMaceMod.toggleXrayKey,
                () -> ShieldMaceSettings.INSTANCE.xrayEnabled,
                v -> ShieldMaceSettings.INSTANCE.xrayEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Range (blocks)", 1, 64,
                            () -> ShieldMaceSettings.INSTANCE.xrayRangeBlocks,
                            v -> ShieldMaceSettings.INSTANCE.xrayRangeBlocks = v)
                }, new BoolToggleSpec[0]));
        allCards.add(card("ESP", "Render",
                () -> ShieldMaceMod.toggleEspKey,
                () -> ShieldMaceSettings.INSTANCE.espEnabled,
                v -> ShieldMaceSettings.INSTANCE.espEnabled = v,
                new SettingSpec[0],
                new BoolToggleSpec[]{
                    new BoolToggleSpec("Show Players",
                            () -> ShieldMaceSettings.INSTANCE.espShowPlayers,
                            v -> ShieldMaceSettings.INSTANCE.espShowPlayers = v),
                    new BoolToggleSpec("Show Hostile Mobs",
                            () -> ShieldMaceSettings.INSTANCE.espShowHostile,
                            v -> ShieldMaceSettings.INSTANCE.espShowHostile = v),
                    new BoolToggleSpec("Show Passive Mobs",
                            () -> ShieldMaceSettings.INSTANCE.espShowPassive,
                            v -> ShieldMaceSettings.INSTANCE.espShowPassive = v),
                    new BoolToggleSpec("Show Items",
                            () -> ShieldMaceSettings.INSTANCE.espShowItems,
                            v -> ShieldMaceSettings.INSTANCE.espShowItems = v)
                }));
        allCards.add(card("Tracers", "Render",
                () -> ShieldMaceMod.toggleTracersKey,
                () -> ShieldMaceSettings.INSTANCE.tracersEnabled,
                v -> ShieldMaceSettings.INSTANCE.tracersEnabled = v,
                new SettingSpec[0],
                new BoolToggleSpec[]{
                    new BoolToggleSpec("Show Players",
                            () -> ShieldMaceSettings.INSTANCE.tracersShowPlayers,
                            v -> ShieldMaceSettings.INSTANCE.tracersShowPlayers = v),
                    new BoolToggleSpec("Show Hostile Mobs",
                            () -> ShieldMaceSettings.INSTANCE.tracersShowHostile,
                            v -> ShieldMaceSettings.INSTANCE.tracersShowHostile = v),
                    new BoolToggleSpec("Show Passive Mobs",
                            () -> ShieldMaceSettings.INSTANCE.tracersShowPassive,
                            v -> ShieldMaceSettings.INSTANCE.tracersShowPassive = v)
                }));
        allCards.add(card("Fullbright", "Render",
                () -> ShieldMaceMod.toggleFullbrightKey,
                () -> ShieldMaceSettings.INSTANCE.fullbrightEnabled,
                v -> ShieldMaceSettings.INSTANCE.fullbrightEnabled = v,
                new SettingSpec[0], new BoolToggleSpec[0]));
        allCards.add(card("NameTags", "Render",
                () -> ShieldMaceMod.toggleNameTagsKey,
                () -> ShieldMaceSettings.INSTANCE.nameTagsEnabled,
                v -> ShieldMaceSettings.INSTANCE.nameTagsEnabled = v,
                new SettingSpec[]{
                    new SettingSpec("Range (blocks)", 8, 256,
                            () -> ShieldMaceSettings.INSTANCE.nameTagsRangeBlocks,
                            v -> ShieldMaceSettings.INSTANCE.nameTagsRangeBlocks = v)
                }, new BoolToggleSpec[0]));
    }

    private static FeatureCard card(String name, String category,
                                    KeyBindingSupplier kb, BoolGetter g, BoolSetter s,
                                    SettingSpec[] settings, BoolToggleSpec[] bools) {
        return new FeatureCard(name, category, kb, g, s, settings, bools);
    }

    private void recomputeVisibleCards() {
        visibleCards.clear();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        for (FeatureCard c : allCards) {
            if (q.isEmpty()
                    || c.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || c.category.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                visibleCards.add(c);
            }
        }
    }

    private int viewportTop() { return CONTENT_TOP; }
    private int viewportBottom() { return this.height - 32; }

    private void layoutAndAddWidgets() {
        clearChildren();
        if (searchBox != null) addDrawableChild(searchBox);

        recomputeVisibleCards();

        int cardX = (this.width - CARD_WIDTH) / 2;
        int y = viewportTop() - scrollY;

        int drawnTop = y;

        for (int i = 0; i < visibleCards.size(); i++) {
            FeatureCard card = visibleCards.get(i);
            card.x = cardX;
            card.y = y;

            int cardHeight = CARD_HEADER_HEIGHT;
            if (card.expanded) {
                cardHeight += CARD_INNER_PADDING + WIDGET_HEIGHT;                       // keybind row
                cardHeight += card.boolToggles.length * (WIDGET_HEIGHT + CARD_INNER_PADDING);
                cardHeight += card.settings.length    * (WIDGET_HEIGHT + CARD_INNER_PADDING);
                cardHeight += CARD_INNER_PADDING;
            }
            card.height = cardHeight;

            // Cull cards entirely outside the viewport — don't bother adding their widgets.
            boolean cardOnScreen = (card.y + cardHeight) > viewportTop()
                                && card.y < viewportBottom();

            if (cardOnScreen) {
                // ── Header row: ON/OFF toggle button on the left
                int toggleBtnW = 50;
                ButtonWidget toggleBtn = ButtonWidget.builder(
                        Text.literal(card.isEnabled() ? "ON" : "OFF"),
                        b -> {
                            boolean newVal = !card.isEnabled();
                            card.setEnabled(newVal);
                            if (ShieldMaceMod.feature != null) ShieldMaceMod.feature.resetRuntimeState();
                            if (ShieldMaceMod.pearlInterceptor != null) ShieldMaceMod.pearlInterceptor.resetRuntimeState();
                            layoutAndAddWidgets();
                        })
                    .dimensions(card.x + CARD_INNER_PADDING,
                                card.y + (CARD_HEADER_HEIGHT - WIDGET_HEIGHT) / 2,
                                toggleBtnW, WIDGET_HEIGHT)
                    .build();
                addDrawableChild(toggleBtn);

                if (card.expanded) {
                    int rowY = card.y + CARD_HEADER_HEIGHT + CARD_INNER_PADDING;

                    // ── Keybind row (always present in expanded card) ──
                    int kbBtnX = card.x + CARD_INNER_PADDING + 90;
                    int kbBtnW = CARD_WIDTH - CARD_INNER_PADDING * 2 - 90;
                    final int cardIdx = i;
                    ButtonWidget keybindBtn = ButtonWidget.builder(
                            Text.literal(keybindButtonLabel(cardIdx)),
                            b -> {
                                pendingRebind = cardIdx;
                                layoutAndAddWidgets();
                            })
                        .dimensions(kbBtnX, rowY, kbBtnW, WIDGET_HEIGHT)
                        .build();
                    addDrawableChild(keybindBtn);
                    rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;

                    // ── Bool toggle rows ──
                    for (BoolToggleSpec spec : card.boolToggles) {
                        final BoolToggleSpec specRef = spec;
                        int btnW = 60;
                        int btnX = card.x + CARD_WIDTH - CARD_INNER_PADDING - btnW;
                        ButtonWidget subToggleBtn = ButtonWidget.builder(
                                Text.literal(specRef.getter.get() ? "ON" : "OFF"),
                                b -> {
                                    specRef.setter.set(!specRef.getter.get());
                                    layoutAndAddWidgets();
                                })
                            .dimensions(btnX, rowY, btnW, WIDGET_HEIGHT)
                            .build();
                        addDrawableChild(subToggleBtn);
                        rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;
                    }

                    // ── Setting sliders ──
                    for (SettingSpec spec : card.settings) {
                        addDrawableChild(new IntSliderWidget(
                                card.x + CARD_INNER_PADDING, rowY,
                                CARD_WIDTH - CARD_INNER_PADDING * 2, WIDGET_HEIGHT,
                                spec.label, spec.min, spec.max,
                                spec.getter.getAsInt(), spec.setter
                        ));
                        rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;
                    }
                }
            }

            y += cardHeight + CARD_GAP;
        }

        contentHeight = y - drawnTop;

        // Done button is fixed at the bottom of the screen.
        int doneW = 80;
        ButtonWidget doneBtn = ButtonWidget.builder(
                Text.literal("Done"),
                b -> close())
            .dimensions((this.width - doneW) / 2, this.height - 26, doneW, WIDGET_HEIGHT)
            .build();
        addDrawableChild(doneBtn);

        // Clamp scroll within bounds (in case content shrank after a search).
        clampScroll();
    }

    private void clampScroll() {
        int viewport = viewportBottom() - viewportTop();
        int max = Math.max(0, contentHeight - viewport + 16);
        if (scrollY < 0)   scrollY = 0;
        if (scrollY > max) scrollY = max;
    }

    private String keybindButtonLabel(int idx) {
        if (pendingRebind == idx) return "> Press a key (Esc to clear) <";
        KeyBinding kb = visibleCards.get(idx).keyBindingSupplier.get();
        return "Keybind: " + kb.getBoundKeyLocalizedText().getString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 14, COLOR_TITLE);

        // "Search:" label to the left of the search box
        if (searchBox != null) {
            String label = "Search:";
            int lw = this.textRenderer.getWidth(label);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                    searchBox.getX() - lw - 6,
                    searchBox.getY() + (WIDGET_HEIGHT - this.textRenderer.fontHeight) / 2,
                    COLOR_LABEL);
        }

        // Card area is clipped to a viewport so cards don't leak over the
        // top bar / done button as the user scrolls.
        context.enableScissor(0, viewportTop(), this.width, viewportBottom());

        for (int i = 0; i < visibleCards.size(); i++) {
            FeatureCard card = visibleCards.get(i);
            // Skip drawing for fully-offscreen cards (they have no widgets either).
            if (card.y + card.height < viewportTop() || card.y > viewportBottom()) continue;

            boolean hover = mouseX >= card.x && mouseX <= card.x + CARD_WIDTH
                    && mouseY >= card.y && mouseY <= card.y + card.height;

            int bg = hover ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG;
            context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + card.height, bg);
            context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + 1, COLOR_CARD_BORDER);
            context.fill(card.x, card.y + card.height - 1,
                    card.x + CARD_WIDTH, card.y + card.height, COLOR_CARD_BORDER);
            context.fill(card.x, card.y, card.x + 1, card.y + card.height, COLOR_CARD_BORDER);
            context.fill(card.x + CARD_WIDTH - 1, card.y,
                    card.x + CARD_WIDTH, card.y + card.height, COLOR_CARD_BORDER);

            // Feature name + category
            int nameX = card.x + CARD_INNER_PADDING + 50 + 8;
            int nameY = card.y + (CARD_HEADER_HEIGHT - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(card.name), nameX, nameY, COLOR_LABEL);
            int nameW = this.textRenderer.getWidth(card.name);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("[" + card.category + "]"),
                    nameX + nameW + 6, nameY, COLOR_CATEGORY);

            // Right-side hint
            String hint = card.expanded ? "right-click to collapse" : "right-click to expand";
            int hintW = this.textRenderer.getWidth(hint);
            context.drawTextWithShadow(this.textRenderer, Text.literal(hint),
                    card.x + CARD_WIDTH - CARD_INNER_PADDING - hintW, nameY, COLOR_HINT);

            // Inside expanded card — labels for keybind and bool toggles.
            if (card.expanded) {
                int rowY = card.y + CARD_HEADER_HEIGHT + CARD_INNER_PADDING;
                int labelY = rowY + (WIDGET_HEIGHT - this.textRenderer.fontHeight) / 2;
                context.drawTextWithShadow(this.textRenderer, Text.literal("Keybind"),
                        card.x + CARD_INNER_PADDING, labelY, COLOR_LABEL);
                rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;

                for (BoolToggleSpec spec : card.boolToggles) {
                    int boolLabelY = rowY + (WIDGET_HEIGHT - this.textRenderer.fontHeight) / 2;
                    context.drawTextWithShadow(this.textRenderer,
                            Text.literal(spec.label),
                            card.x + CARD_INNER_PADDING, boolLabelY, COLOR_LABEL);
                    rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;
                }

                if (pendingRebind == i) {
                    int promptY = card.y + card.height + 2;
                    context.drawCenteredTextWithShadow(this.textRenderer,
                            Text.literal("Press a key to rebind, or Esc to clear"),
                            card.x + CARD_WIDTH / 2, promptY, COLOR_PROMPT);
                }
            }
        }

        context.disableScissor();

        // Scrollbar (right side of the viewport)
        int viewportH = viewportBottom() - viewportTop();
        if (contentHeight > viewportH) {
            int trackX = (this.width + CARD_WIDTH) / 2 + 4;
            int trackW = 4;
            int trackY = viewportTop();
            int trackH = viewportH;
            context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x40FFFFFF);

            int thumbH = Math.max(20, (int) ((long) trackH * trackH / contentHeight));
            int maxScroll = Math.max(1, contentHeight - viewportH + 16);
            int thumbY = trackY + (int) ((long) (trackH - thumbH) * scrollY / maxScroll);
            context.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, 0xC0FFFFFF);
        }

        // Filter result count
        if (!searchQuery.trim().isEmpty()) {
            String msg = visibleCards.size() + " of " + allCards.size() + " features";
            context.drawTextWithShadow(this.textRenderer, Text.literal(msg),
                    8, this.height - 22, COLOR_HINT);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // If we're capturing a keybind and the user clicks a mouse button, bind to it.
        if (pendingRebind != -1) {
            // Ignore clicks inside the search box so the user can still use it.
            if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(click, doubled);
            }
            KeyBinding kb = visibleCards.get(pendingRebind).keyBindingSupplier.get();
            kb.setBoundKey(InputUtil.Type.MOUSE.createFromCode(button));
            KeyBinding.updateKeysByCode();
            persistKeybinds();
            pendingRebind = -1;
            layoutAndAddWidgets();
            return true;
        }

        if (super.mouseClicked(click, doubled)) return true;

        // Right-click on a card header → expand/collapse
        if (button == 1) {
            for (FeatureCard card : visibleCards) {
                if (mouseX >= card.x && mouseX <= card.x + CARD_WIDTH
                        && mouseY >= card.y && mouseY <= card.y + CARD_HEADER_HEIGHT
                        && mouseY >= viewportTop() && mouseY <= viewportBottom()) {
                    card.expanded = !card.expanded;
                    layoutAndAddWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount)) return true;
        if (mouseY < viewportTop() || mouseY > viewportBottom()) return false;
        scrollY -= (int) (vertAmount * 24);
        clampScroll();
        layoutAndAddWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (pendingRebind != -1) {
            KeyBinding kb = visibleCards.get(pendingRebind).keyBindingSupplier.get();
            if (input.key() == GLFW_KEY_ESCAPE) {
                kb.setBoundKey(InputUtil.UNKNOWN_KEY);
            } else {
                kb.setBoundKey(InputUtil.fromKeyCode(input));
            }
            KeyBinding.updateKeysByCode();
            persistKeybinds();
            pendingRebind = -1;
            layoutAndAddWidgets();
            return true;
        }
        return super.keyPressed(input);
    }

    private void persistKeybinds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.write();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ── Internal types ────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface BoolGetter { boolean get(); }
    @FunctionalInterface
    private interface BoolSetter { void set(boolean v); }
    @FunctionalInterface
    private interface IntGetter { int getAsInt(); }
    @FunctionalInterface
    private interface KeyBindingSupplier { KeyBinding get(); }

    private static final class SettingSpec {
        final String label;
        final int min;
        final int max;
        final IntGetter getter;
        final IntConsumer setter;
        SettingSpec(String label, int min, int max, IntGetter getter, IntConsumer setter) {
            this.label = label; this.min = min; this.max = max;
            this.getter = getter; this.setter = setter;
        }
    }

    private static final class BoolToggleSpec {
        final String label;
        final BoolGetter getter;
        final BoolSetter setter;
        BoolToggleSpec(String label, BoolGetter getter, BoolSetter setter) {
            this.label = label; this.getter = getter; this.setter = setter;
        }
    }

    private static final class FeatureCard {
        final String name;
        final String category;
        final KeyBindingSupplier keyBindingSupplier;
        final BoolGetter enabledGetter;
        final BoolSetter enabledSetter;
        final SettingSpec[] settings;
        final BoolToggleSpec[] boolToggles;

        boolean expanded = false;
        int x, y, height;

        FeatureCard(String name, String category,
                    KeyBindingSupplier keyBindingSupplier,
                    BoolGetter enabledGetter, BoolSetter enabledSetter,
                    SettingSpec[] settings, BoolToggleSpec[] boolToggles) {
            this.name = name;
            this.category = category;
            this.keyBindingSupplier = keyBindingSupplier;
            this.enabledGetter = enabledGetter;
            this.enabledSetter = enabledSetter;
            this.settings = settings;
            this.boolToggles = boolToggles;
        }

        boolean isEnabled() { return enabledGetter.get(); }
        void setEnabled(boolean v) { enabledSetter.set(v); }
    }

    /** A SliderWidget that maps the [0..1] value to an integer in [min..max]. */
    private static final class IntSliderWidget extends SliderWidget {
        private final int min;
        private final int max;
        private final String label;
        private final IntConsumer onChange;
        private int currentValue;

        IntSliderWidget(int x, int y, int width, int height, String label,
                        int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height,
                    Text.literal(label + ": " + initial),
                    (initial - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.currentValue = clamp(initial);
            this.onChange = onChange;
            updateMessage();
        }

        private int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            currentValue = clamp((int) Math.round(min + value * (max - min)));
            setMessage(Text.literal(label + ": " + currentValue));
        }

        @Override
        protected void applyValue() {
            currentValue = clamp((int) Math.round(min + value * (max - min)));
            onChange.accept(currentValue);
        }
    }
}
