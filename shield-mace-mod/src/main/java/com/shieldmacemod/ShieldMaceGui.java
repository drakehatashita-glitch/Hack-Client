package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ShieldMaceGui extends Screen {

    private static final int CARD_WIDTH         = 300;
    private static final int CARD_HEADER_HEIGHT = 28;
    private static final int CARD_INNER_PADDING = 4;
    private static final int CARD_GAP           = 6;
    private static final int WIDGET_HEIGHT      = 20;
    private static final int CONTENT_TOP        = 36;

    private static final int COLOR_TITLE          = 0xFFFFFFFF;
    private static final int COLOR_CARD_BG        = 0xC0202020;
    private static final int COLOR_CARD_BG_HOVER  = 0xC0303040;
    private static final int COLOR_CARD_BORDER    = 0xFF505060;
    private static final int COLOR_LABEL          = 0xFFE0E0E0;
    private static final int COLOR_HINT           = 0xFF9090A0;
    private static final int COLOR_PROMPT         = 0xFFFFD040;

    // GLFW key codes (inlined)
    private static final int GLFW_KEY_UNKNOWN = -1;
    private static final int GLFW_KEY_ESCAPE  = 256;

    private final List<FeatureCard> cards = new ArrayList<>();

    /** Index into cards[] of the feature whose keybind we're currently capturing, or -1. */
    private int pendingRebind = -1;

    public ShieldMaceGui() {
        super(Text.literal("Shield Mace Mod"));
    }

    @Override
    protected void init() {
        if (cards.isEmpty()) {
            cards.add(new FeatureCard(
                    "Shield Mace Combo",
                    () -> ShieldMaceMod.toggleComboKey,
                    () -> ShieldMaceSettings.INSTANCE.comboEnabled,
                    enabled -> ShieldMaceSettings.INSTANCE.comboEnabled = enabled,
                    new SettingSpec[]{
                        new SettingSpec("Swap Delay (ticks)", 1, 10,
                                () -> ShieldMaceSettings.INSTANCE.comboSwapDelayTicks,
                                v -> ShieldMaceSettings.INSTANCE.comboSwapDelayTicks = v),
                        new SettingSpec("Cooldown (ticks)", 1, 40,
                                () -> ShieldMaceSettings.INSTANCE.comboCooldownTicks,
                                v -> ShieldMaceSettings.INSTANCE.comboCooldownTicks = v)
                    },
                    new BoolToggleSpec[0],
                    "toggleCombo"
            ));
            cards.add(new FeatureCard(
                    "Breach Mace Swap",
                    () -> ShieldMaceMod.toggleBreachSwapKey,
                    () -> ShieldMaceSettings.INSTANCE.breachSwapEnabled,
                    enabled -> ShieldMaceSettings.INSTANCE.breachSwapEnabled = enabled,
                    new SettingSpec[]{
                        new SettingSpec("Swap Delay (ticks)", 1, 10,
                                () -> ShieldMaceSettings.INSTANCE.breachSwapDelayTicks,
                                v -> ShieldMaceSettings.INSTANCE.breachSwapDelayTicks = v),
                        new SettingSpec("Cooldown (ticks)", 1, 40,
                                () -> ShieldMaceSettings.INSTANCE.breachSwapCooldownTicks,
                                v -> ShieldMaceSettings.INSTANCE.breachSwapCooldownTicks = v)
                    },
                    new BoolToggleSpec[0],
                    "toggleBreachSwap"
            ));
            cards.add(new FeatureCard(
                    "Mace Spam",
                    () -> ShieldMaceMod.toggleMaceSpamKey,
                    () -> ShieldMaceSettings.INSTANCE.maceSpamEnabled,
                    enabled -> ShieldMaceSettings.INSTANCE.maceSpamEnabled = enabled,
                    new SettingSpec[]{
                        new SettingSpec("Clicks per tick", 1, 100,
                                () -> ShieldMaceSettings.INSTANCE.maceSpamClicksPerTick,
                                v -> ShieldMaceSettings.INSTANCE.maceSpamClicksPerTick = v)
                    },
                    new BoolToggleSpec[]{
                        new BoolToggleSpec("Smart Fall Click (U3 shield break)",
                                () -> ShieldMaceSettings.INSTANCE.maceSpamSmartFallClick,
                                v -> ShieldMaceSettings.INSTANCE.maceSpamSmartFallClick = v)
                    },
                    "toggleMaceSpam"
            ));
            cards.add(new FeatureCard(
                    "Silent Aim",
                    () -> ShieldMaceMod.toggleSilentAimKey,
                    () -> ShieldMaceSettings.INSTANCE.silentAimEnabled,
                    enabled -> ShieldMaceSettings.INSTANCE.silentAimEnabled = enabled,
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
                    },
                    new BoolToggleSpec[0],
                    "toggleSilentAim"
            ));
            cards.add(new FeatureCard(
                    "Pearl Wind-Charge Intercept",
                    () -> ShieldMaceMod.togglePearlInterceptKey,
                    () -> ShieldMaceSettings.INSTANCE.pearlInterceptEnabled,
                    enabled -> ShieldMaceSettings.INSTANCE.pearlInterceptEnabled = enabled,
                    new SettingSpec[]{
                        new SettingSpec("Tolerance x0.1 blocks", 1, 30,
                                () -> ShieldMaceSettings.INSTANCE.pearlInterceptToleranceTenths,
                                v -> ShieldMaceSettings.INSTANCE.pearlInterceptToleranceTenths = v),
                        new SettingSpec("Lookahead (ticks)", 10, 80,
                                () -> ShieldMaceSettings.INSTANCE.pearlInterceptLookahead,
                                v -> ShieldMaceSettings.INSTANCE.pearlInterceptLookahead = v)
                    },
                    new BoolToggleSpec[0],
                    "togglePearlIntercept"
            ));
        }

        layoutAndAddWidgets();
    }

    private void layoutAndAddWidgets() {
        clearChildren();

        int cardX = (this.width - CARD_WIDTH) / 2;
        int y = CONTENT_TOP;

        for (int i = 0; i < cards.size(); i++) {
            FeatureCard card = cards.get(i);
            card.x = cardX;
            card.y = y;

            // ── Header row (always visible): ON/OFF button on the left ─────────
            int toggleBtnW = 50;
            ButtonWidget toggleBtn = ButtonWidget.builder(
                    Text.literal(card.isEnabled() ? "ON" : "OFF"),
                    b -> {
                        boolean newVal = !card.isEnabled();
                        card.setEnabled(newVal);
                        // Reset internal state so toggling mid-action can't glitch
                        if (ShieldMaceMod.feature != null) {
                            ShieldMaceMod.feature.resetRuntimeState();
                        }
                        if (ShieldMaceMod.pearlInterceptor != null) {
                            ShieldMaceMod.pearlInterceptor.resetRuntimeState();
                        }
                        layoutAndAddWidgets();
                    })
                .dimensions(card.x + CARD_INNER_PADDING,
                            card.y + (CARD_HEADER_HEIGHT - WIDGET_HEIGHT) / 2,
                            toggleBtnW, WIDGET_HEIGHT)
                .build();
            addDrawableChild(toggleBtn);

            // Card body height (computed to know where the next card goes)
            int cardHeight = CARD_HEADER_HEIGHT;

            if (card.expanded) {
                int rowY = card.y + CARD_HEADER_HEIGHT + CARD_INNER_PADDING;

                // ── Keybind row ───────────────────────────────────────────────
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

                // ── Bool toggle rows ──────────────────────────────────────────
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

                // ── Setting sliders ───────────────────────────────────────────
                for (SettingSpec spec : card.settings) {
                    addDrawableChild(new IntSliderWidget(
                            card.x + CARD_INNER_PADDING, rowY,
                            CARD_WIDTH - CARD_INNER_PADDING * 2, WIDGET_HEIGHT,
                            spec.label, spec.min, spec.max,
                            spec.getter.getAsInt(),
                            spec.setter
                    ));
                    rowY += WIDGET_HEIGHT + CARD_INNER_PADDING;
                }

                cardHeight = rowY - card.y;
            }

            card.height = cardHeight;
            y += cardHeight + CARD_GAP;
        }

        // ── Done button at the bottom ─────────────────────────────────────────
        int doneW = 100;
        ButtonWidget doneBtn = ButtonWidget.builder(
                Text.literal("Done"),
                b -> close())
            .dimensions((this.width - doneW) / 2, y + 6, doneW, WIDGET_HEIGHT)
            .build();
        addDrawableChild(doneBtn);
    }

    private String keybindButtonLabel(int idx) {
        if (pendingRebind == idx) return "> Press a key (Esc to clear) <";
        KeyBinding kb = cards.get(idx).keyBindingSupplier.get();
        return "Keybind: " + kb.getBoundKeyLocalizedText().getString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 14, COLOR_TITLE);

        // Card backgrounds, borders, labels, hints
        for (int i = 0; i < cards.size(); i++) {
            FeatureCard card = cards.get(i);
            boolean hover = mouseX >= card.x && mouseX <= card.x + CARD_WIDTH
                    && mouseY >= card.y && mouseY <= card.y + card.height;

            int bg = hover ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG;
            context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + card.height, bg);
            // 1-px border
            context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + 1, COLOR_CARD_BORDER);
            context.fill(card.x, card.y + card.height - 1,
                    card.x + CARD_WIDTH, card.y + card.height, COLOR_CARD_BORDER);
            context.fill(card.x, card.y, card.x + 1, card.y + card.height, COLOR_CARD_BORDER);
            context.fill(card.x + CARD_WIDTH - 1, card.y,
                    card.x + CARD_WIDTH, card.y + card.height, COLOR_CARD_BORDER);

            // Feature name (right of the ON/OFF button)
            int nameX = card.x + CARD_INNER_PADDING + 50 + 8;
            int nameY = card.y + (CARD_HEADER_HEIGHT - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(card.name), nameX, nameY, COLOR_LABEL);

            // Right-side hint
            String hint = card.expanded ? "right-click to collapse" : "right-click to expand";
            int hintW = this.textRenderer.getWidth(hint);
            context.drawTextWithShadow(this.textRenderer, Text.literal(hint),
                    card.x + CARD_WIDTH - CARD_INNER_PADDING - hintW, nameY, COLOR_HINT);

            // "Keybind:" label and bool-toggle labels inside expanded card
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
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // If we're capturing a keybind and the user clicks a mouse button,
        // bind to that mouse button.
        if (pendingRebind != -1) {
            KeyBinding kb = cards.get(pendingRebind).keyBindingSupplier.get();
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
            for (FeatureCard card : cards) {
                if (mouseX >= card.x && mouseX <= card.x + CARD_WIDTH
                        && mouseY >= card.y && mouseY <= card.y + CARD_HEADER_HEIGHT) {
                    card.expanded = !card.expanded;
                    layoutAndAddWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (pendingRebind != -1) {
            KeyBinding kb = cards.get(pendingRebind).keyBindingSupplier.get();
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
            this.label = label;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private static final class BoolToggleSpec {
        final String label;
        final BoolGetter getter;
        final BoolSetter setter;

        BoolToggleSpec(String label, BoolGetter getter, BoolSetter setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private static final class FeatureCard {
        final String name;
        final KeyBindingSupplier keyBindingSupplier;
        final BoolGetter enabledGetter;
        final BoolSetter enabledSetter;
        final SettingSpec[] settings;
        final BoolToggleSpec[] boolToggles;
        @SuppressWarnings("unused") final String translationSuffix;

        boolean expanded = false;
        int x, y, height;

        FeatureCard(String name,
                    KeyBindingSupplier keyBindingSupplier,
                    BoolGetter enabledGetter,
                    BoolSetter enabledSetter,
                    SettingSpec[] settings,
                    BoolToggleSpec[] boolToggles,
                    String translationSuffix) {
            this.name = name;
            this.keyBindingSupplier = keyBindingSupplier;
            this.enabledGetter = enabledGetter;
            this.enabledSetter = enabledSetter;
            this.settings = settings;
            this.boolToggles = boolToggles;
            this.translationSuffix = translationSuffix;
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
