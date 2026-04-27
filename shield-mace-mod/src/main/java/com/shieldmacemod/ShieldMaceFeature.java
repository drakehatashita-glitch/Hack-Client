package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
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

    private boolean enabled = false;

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
            String msg = enabled ? "Shield Mace Combo: ON" : "Shield Mace Combo: OFF";
            client.player.sendMessage(Text.literal(msg), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (!enabled) return;
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        switch (state) {
            case IDLE          -> handleIdle(client);
            case AXE_SWUNG     -> {
                if (delayTimer > 0) delayTimer--;
                else state = State.WAITING_FOR_MACE;
            }
            case WAITING_FOR_MACE -> handleMaceSwing(client);
        }
    }

    // ── Idle: find target, equip axe, swing (disables shield) ─────────────────

    private void handleIdle(MinecraftClient client) {
        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) return;

        int axeSlot = findAxeSlot(client);
        if (axeSlot == -1) return;

        client.player.getInventory().setSelectedSlot(axeSlot);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);

        state = State.AXE_SWUNG;
        delayTimer = SWAP_DELAY_TICKS;
    }

    // ── After delay: equip best mace, swing ───────────────────────────────────

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

        state = State.IDLE;
        cooldownTimer = COMBO_COOLDOWN_TICKS;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the player the local player is looking at via crosshair, or null.
     */
    private PlayerEntity getLookedAtPlayer(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (entity instanceof PlayerEntity target && target != client.player) {
            return target;
        }
        return null;
    }

    /**
     * Finds an axe in hotbar slots 0-8.
     * Stays on current slot if it is already an axe.
     */
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

    /**
     * Finds the best mace in hotbar slots 0-8.
     * Priority: density enchantment > breach enchantment > plain mace.
     */
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
