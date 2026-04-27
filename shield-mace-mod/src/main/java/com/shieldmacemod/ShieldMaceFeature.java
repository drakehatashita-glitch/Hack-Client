package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.item.enchantment.EnchantmentHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ShieldMaceFeature {

    // How many ticks to wait between the axe hit and mace swap/hit.
    // 1 tick = 50ms. 3 ticks gives time for the attack packet to register.
    private static final int SWAP_DELAY_TICKS = 3;
    // How many ticks to wait between full combo cycles to avoid spamming.
    private static final int COMBO_COOLDOWN_TICKS = 10;

    private boolean enabled = false;

    // State machine
    private enum State { IDLE, AXE_SWUNG, WAITING_FOR_MACE }

    private State state = State.IDLE;
    private int delayTimer = 0;
    private int cooldownTimer = 0;

    public void toggle(MinecraftClient client) {
        enabled = !enabled;
        state = State.IDLE;
        delayTimer = 0;
        cooldownTimer = 0;

        if (client.player != null) {
            String key = enabled ? "text.shieldmacemod.enabled" : "text.shieldmacemod.disabled";
            client.player.sendMessage(Text.translatable(key), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (!enabled) return;
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        // Decrement cooldown
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        // --- State machine ---
        switch (state) {
            case IDLE -> handleIdle(client);
            case AXE_SWUNG -> {
                if (delayTimer > 0) {
                    delayTimer--;
                } else {
                    state = State.WAITING_FOR_MACE;
                }
            }
            case WAITING_FOR_MACE -> handleMaceSwing(client);
        }
    }

    private void handleIdle(MinecraftClient client) {
        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) return;

        // Find axe slot in hotbar (slots 0-8)
        int axeSlot = findAxeSlot(client);
        if (axeSlot == -1) return;

        // Switch to axe
        client.player.getInventory().selectedSlot = axeSlot;

        // Attack the target — this disables their shield if they are blocking
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);

        state = State.AXE_SWUNG;
        delayTimer = SWAP_DELAY_TICKS;
    }

    private void handleMaceSwing(MinecraftClient client) {
        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) {
            // Lost sight — reset
            state = State.IDLE;
            return;
        }

        // Find the best mace: density > breach > any mace
        int maceSlot = findBestMaceSlot(client);
        if (maceSlot == -1) {
            // No mace found — reset
            state = State.IDLE;
            return;
        }

        // Switch to mace
        client.player.getInventory().selectedSlot = maceSlot;

        // Attack with mace
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);

        // Full combo done — enter cooldown before next cycle
        state = State.IDLE;
        cooldownTimer = COMBO_COOLDOWN_TICKS;
    }

    /**
     * Returns the player the local player is currently looking at, or null.
     * Uses the crosshair entity target if it is a PlayerEntity.
     */
    private PlayerEntity getLookedAtPlayer(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (entity instanceof PlayerEntity targetPlayer && targetPlayer != client.player) {
            return targetPlayer;
        }
        return null;
    }

    /**
     * Find any axe in hotbar slots 0-8.
     * Prefers an axe already in hand to avoid unnecessary swaps.
     */
    private int findAxeSlot(MinecraftClient client) {
        int current = client.player.getInventory().selectedSlot;
        ItemStack currentStack = client.player.getInventory().getStack(current);
        if (currentStack.getItem() instanceof AxeItem) return current;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    /**
     * Find the best mace in hotbar slots 0-8.
     * Priority: density enchantment > breach enchantment > unenchanted mace.
     * Returns -1 if no mace is found.
     */
    private int findBestMaceSlot(MinecraftClient client) {
        int densitySlot = -1;
        int breachSlot = -1;
        int plainMaceSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof MaceItem)) continue;

            if (hasDensity(client, stack)) {
                // Highest priority — take first density mace found
                if (densitySlot == -1) densitySlot = i;
            } else if (hasBreach(client, stack)) {
                if (breachSlot == -1) breachSlot = i;
            } else {
                if (plainMaceSlot == -1) plainMaceSlot = i;
            }
        }

        if (densitySlot != -1) return densitySlot;
        if (breachSlot != -1) return breachSlot;
        return plainMaceSlot;
    }

    private boolean hasDensity(MinecraftClient client, ItemStack stack) {
        var registry = client.world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var densityEntry = registry.getEntry(net.minecraft.enchantment.Enchantments.DENSITY);
        return densityEntry.isPresent() && EnchantmentHelper.getLevel(densityEntry.get(), stack) > 0;
    }

    private boolean hasBreach(MinecraftClient client, ItemStack stack) {
        var registry = client.world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var breachEntry = registry.getEntry(net.minecraft.enchantment.Enchantments.BREACH);
        return breachEntry.isPresent() && EnchantmentHelper.getLevel(breachEntry.get(), stack) > 0;
    }
}
