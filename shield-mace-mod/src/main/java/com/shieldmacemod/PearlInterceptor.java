package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WindChargeItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches for the player throwing an ender pearl, then computes the angle and
 * timing required for a wind charge fired from the player's hotbar to intercept
 * that pearl mid-flight. When a valid intercept is found it snaps the player's
 * yaw/pitch, fires the wind charge, then restores the slot and rotation.
 */
public class PearlInterceptor {

    // Physics constants (pearl approximation)
    private static final double WIND_CHARGE_SPEED = 1.5;   // blocks/tick (initial throw speed)
    private static final double PEARL_DRAG        = 0.99;
    private static final double PEARL_GRAVITY     = 0.03;

    private static final double SPAWN_DETECT_RADIUS = 4.0;
    private static final int    MAX_AIMING_TICKS    = 40;

    private final ShieldMaceSettings s = ShieldMaceSettings.INSTANCE;

    private enum State { IDLE, AIMING }

    private State state = State.IDLE;
    private EnderPearlEntity targetPearl = null;
    private int previousSlot = -1;
    private float originalYaw = 0;
    private float originalPitch = 0;
    private int ticksInAiming = 0;

    private final Set<Integer> trackedPearlIds = new HashSet<>();

    public void toggle(MinecraftClient client) {
        s.pearlInterceptEnabled = !s.pearlInterceptEnabled;
        resetRuntimeState();
        announce(client, s.pearlInterceptEnabled ? "Pearl Catch: ON" : "Pearl Catch: OFF");
    }

    public void resetRuntimeState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && previousSlot >= 0 && previousSlot < 9) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        state = State.IDLE;
        targetPearl = null;
        previousSlot = -1;
        ticksInAiming = 0;
        trackedPearlIds.clear();
    }

    private void announce(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (!s.pearlInterceptEnabled) {
            if (state != State.IDLE) resetRuntimeState();
            return;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        switch (state) {
            case IDLE   -> handleIdle(client);
            case AIMING -> handleAiming(client);
        }
    }

    // ── Idle: scan for newly spawned ender pearls near the player ────────────

    private void handleIdle(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        Box box = Box.of(player.getEntityPos(), 12, 12, 12);
        List<EnderPearlEntity> nearby = world.getEntitiesByClass(
                EnderPearlEntity.class, box, p -> true);

        Set<Integer> currentIds = new HashSet<>();
        EnderPearlEntity newPearl = null;
        for (EnderPearlEntity p : nearby) {
            currentIds.add(p.getId());
            if (!trackedPearlIds.contains(p.getId())
                    && p.distanceTo(player) < SPAWN_DETECT_RADIUS) {
                newPearl = p;
            }
        }
        // Replace tracked set with what we saw this tick (older ids age out)
        trackedPearlIds.clear();
        trackedPearlIds.addAll(currentIds);

        if (newPearl == null) return;

        int wcSlot = findWindChargeSlot(player);
        if (wcSlot == -1) return;

        previousSlot  = player.getInventory().getSelectedSlot();
        originalYaw   = player.getYaw();
        originalPitch = player.getPitch();
        if (wcSlot != previousSlot) {
            player.getInventory().setSelectedSlot(wcSlot);
        }
        targetPearl   = newPearl;
        ticksInAiming = 0;
        state         = State.AIMING;
    }

    // ── Aiming: each tick try to fire a wind charge that hits the pearl ─────

    private void handleAiming(MinecraftClient client) {
        ticksInAiming++;
        if (targetPearl == null || targetPearl.isRemoved() || ticksInAiming > MAX_AIMING_TICKS) {
            finishAiming(client, false);
            return;
        }

        ClientPlayerEntity player = client.player;
        Vec3d eye = player.getEyePos();
        Vec3d simPos = targetPearl.getEntityPos();
        Vec3d simVel = targetPearl.getVelocity();

        int    maxK      = Math.max(10, s.pearlInterceptLookahead);
        double tolerance = Math.max(0.1, s.pearlInterceptToleranceTenths * 0.1);

        double bestErr = Double.MAX_VALUE;
        Vec3d  bestInterceptPos = null;

        // Find k (wind-charge flight time, in ticks) that best matches the
        // pearl's predicted position k ticks from now.
        for (int k = 1; k <= maxK; k++) {
            simPos = simPos.add(simVel);
            simVel = simVel.multiply(PEARL_DRAG).add(0, -PEARL_GRAVITY, 0);

            double dist = simPos.subtract(eye).length();
            double err  = Math.abs(dist - WIND_CHARGE_SPEED * k);
            if (err < bestErr) {
                bestErr = err;
                bestInterceptPos = simPos;
            }
        }

        if (bestInterceptPos == null || bestErr > tolerance) return;

        // Snap-aim, fire, restore rotation
        Vec3d delta = bestInterceptPos.subtract(eye);
        double yaw   = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double pitch = -Math.toDegrees(Math.atan2(delta.y, horiz));

        player.setYaw((float) yaw);
        player.setPitch((float) pitch);

        ItemStack mainHand = player.getInventory().getStack(
                player.getInventory().getSelectedSlot());
        if (mainHand.getItem() instanceof WindChargeItem) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            player.swingHand(Hand.MAIN_HAND);
            announce(client, "Pearl Catch fired");
        }

        player.setYaw(originalYaw);
        player.setPitch(originalPitch);

        finishAiming(client, true);
    }

    private void finishAiming(MinecraftClient client, boolean success) {
        ClientPlayerEntity player = client.player;
        if (player != null && previousSlot >= 0 && previousSlot < 9) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        if (!success && player != null) {
            player.setYaw(originalYaw);
            player.setPitch(originalPitch);
        }
        previousSlot  = -1;
        targetPearl   = null;
        ticksInAiming = 0;
        state         = State.IDLE;
    }

    private int findWindChargeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof WindChargeItem) {
                return i;
            }
        }
        return -1;
    }
}
