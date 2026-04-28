package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ShieldMaceFeature {

    // Ticks between axe swing and mace swap/swing (1 tick = 50 ms)
    private static final int SWAP_DELAY_TICKS = 3;
    // Ticks between full combo cycles to avoid server-side spam kick
    private static final int COMBO_COOLDOWN_TICKS = 10;

    private boolean enabled = false;            // Feature 1+2: axe/mace auto-combo & shield break
    private boolean breachSwapEnabled = false;  // Feature 3: attack-triggered breach mace swap

    private enum State {
        IDLE,
        // Auto-combo states
        AXE_SWUNG,
        WAITING_FOR_MACE,
        // Click-triggered shield-break states
        SHIELD_BREAK_AXE_SWUNG,
        // Breach-swap states
        BREACH_SWAP_RETURN
    }

    private State state       = State.IDLE;
    private int   delayTimer  = 0;
    private int   cooldownTimer = 0;

    // Slot to restore after a click-triggered swap (shield break or breach swap)
    private int previousSlot  = 0;

    // Edge-detection for attack click (avoids consuming the key event)
    private boolean wasAttackPressed = false;

    public void toggle(MinecraftClient client) {
        enabled = !enabled;
        resetRuntimeState();
        announce(client, enabled ? "Shield Mace Combo: ON" : "Shield Mace Combo: OFF");
    }

    public void toggleBreachSwap(MinecraftClient client) {
        breachSwapEnabled = !breachSwapEnabled;
        resetRuntimeState();
        announce(client, breachSwapEnabled ? "Breach Mace Swap: ON" : "Breach Mace Swap: OFF");
    }

    private void resetRuntimeState() {
        state            = State.IDLE;
        delayTimer       = 0;
        cooldownTimer    = 0;
        wasAttackPressed = false;
    }

    private void announce(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (!enabled && !breachSwapEnabled) return;
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        // ── Click edge-detection (observe only — does NOT consume the key event) ──
        boolean isAttackPressed = client.options.attackKey.isPressed();
        boolean clickedThisTick = isAttackPressed && !wasAttackPressed;
        wasAttackPressed = isAttackPressed;

        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        switch (state) {
            case IDLE -> handleIdle(client, clickedThisTick);
            case AXE_SWUNG -> {
                if (delayTimer > 0) delayTimer--;
                else state = State.WAITING_FOR_MACE;
            }
            case WAITING_FOR_MACE        -> handleMaceSwing(client);
            case SHIELD_BREAK_AXE_SWUNG  -> handleSwapBackReturn(client);
            case BREACH_SWAP_RETURN      -> handleSwapBackReturn(client);
        }
    }

    // ── Idle: breach-swap → click-on-shield → auto-combo ──────────────────────

    private void handleIdle(MinecraftClient client, boolean clickedThisTick) {
        // Priority 1: breach-mace swap on any attack against a mob or player
        if (breachSwapEnabled && clickedThisTick) {
            LivingEntity livingTarget = getLookedAtLiving(client);
            if (livingTarget != null) {
                int breachSlot  = findBreachMaceSlot(client);
                int currentSlot = client.player.getInventory().getSelectedSlot();

                // Only swap if we're not already holding the breach mace
                if (breachSlot != -1 && breachSlot != currentSlot) {
                    previousSlot = currentSlot;
                    client.player.getInventory().setSelectedSlot(breachSlot);
                    client.interactionManager.attackEntity(client.player, livingTarget);
                    client.player.swingHand(Hand.MAIN_HAND);

                    state      = State.BREACH_SWAP_RETURN;
                    delayTimer = SWAP_DELAY_TICKS;
                    return;
                }
            }
        }

        // Features 1 & 2 are gated by the original toggle
        if (!enabled) return;

        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) return;

        // Priority 2: if the user clicked AND target is actively blocking → axe swap-and-return
        if (clickedThisTick && target.isBlocking()) {
            int axeSlot = findAxeSlot(client);
            if (axeSlot != -1) {
                previousSlot = client.player.getInventory().getSelectedSlot();
                // Only swap if we're not already on an axe
                client.player.getInventory().setSelectedSlot(axeSlot);
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);

                state      = State.SHIELD_BREAK_AXE_SWUNG;
                delayTimer = SWAP_DELAY_TICKS;
                return;
            }
        }

        // Priority 3: auto-combo (fires automatically when looking at any player)
        int axeSlot = findAxeSlot(client);
        if (axeSlot == -1) return;

        client.player.getInventory().setSelectedSlot(axeSlot);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);

        state      = State.AXE_SWUNG;
        delayTimer = SWAP_DELAY_TICKS;
    }

    // ── Auto-combo: after delay equip best mace and swing ─────────────────────

    private void handleMaceSwing(MinecraftClient client) {
        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) {
            state = State.IDLE;
            return;
        }

        int maceSlot = findBestMaceSlot(client);
        if (maceSlot == -1) {
            state = State.IDLE;
            return;
        }

        client.player.getInventory().setSelectedSlot(maceSlot);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);

        state         = State.IDLE;
        cooldownTimer = COMBO_COOLDOWN_TICKS;
    }

    // ── Click-shield-break / breach-swap: after delay swap back to previous slot ──

    private void handleSwapBackReturn(MinecraftClient client) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // Swap back to the item the player had before
        client.player.getInventory().setSelectedSlot(previousSlot);

        state         = State.IDLE;
        cooldownTimer = COMBO_COOLDOWN_TICKS;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LivingEntity getLookedAtLiving(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (entity instanceof LivingEntity living && living != client.player) {
            return living;
        }
        return null;
    }

    private PlayerEntity getLookedAtPlayer(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (entity instanceof PlayerEntity target && target != client.player) {
            return target;
        }
        return null;
    }

    private int findAxeSlot(MinecraftClient client) {
        int current = client.player.getInventory().getSelectedSlot();
        if (client.player.getInventory().getStack(current).getItem() instanceof AxeItem) {
            return current;
        }
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    private int findBreachMaceSlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() instanceof MaceItem && hasBreach(client, stack)) return i;
        }
        return -1;
    }

    private int findBestMaceSlot(MinecraftClient client) {
        int densitySlot   = -1;
        int breachSlot    = -1;
        int plainMaceSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof MaceItem)) continue;

            if (densitySlot == -1 && hasDensity(client, stack)) {
                densitySlot = i;
            } else if (breachSlot == -1 && hasBreach(client, stack)) {
                breachSlot = i;
            } else if (plainMaceSlot == -1) {
                plainMaceSlot = i;
            }
        }

        if (densitySlot   != -1) return densitySlot;
        if (breachSlot    != -1) return breachSlot;
        return plainMaceSlot;
    }

    private boolean hasDensity(MinecraftClient client, ItemStack stack) {
        return hasEnchantment(client, stack, Enchantments.DENSITY);
    }

    private boolean hasBreach(MinecraftClient client, ItemStack stack) {
        return hasEnchantment(client, stack, Enchantments.BREACH);
    }

    private boolean hasEnchantment(MinecraftClient client,
                                    ItemStack stack,
                                    net.minecraft.registry.RegistryKey<Enchantment> key) {
        var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> entry = registry.getOptional(key).orElse(null);
        if (entry == null) return false;
        return EnchantmentHelper.getLevel(entry, stack) > 0;
    }
}
