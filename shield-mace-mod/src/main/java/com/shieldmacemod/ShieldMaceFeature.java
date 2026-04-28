package com.shieldmacemod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class ShieldMaceFeature {

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

    private final ShieldMaceSettings s = ShieldMaceSettings.INSTANCE;

    private State state           = State.IDLE;
    private int   delayTimer      = 0;
    private int   cooldownTimer   = 0;

    // Slot to restore after a click-triggered swap (shield break or breach swap)
    private int previousSlot      = 0;

    // Edge-detection for attack click (avoids consuming the key event)
    private boolean wasAttackPressed = false;

    // Auto-totem rate-limiter (ticks remaining until next swap attempt)
    private int autoTotemCooldown = 0;

    // Kill-aura rate-limiter (ticks remaining until next attack)
    private int killAuraCooldown = 0;

    public void toggleCombo(MinecraftClient client) {
        s.comboEnabled = !s.comboEnabled;
        resetRuntimeState();
        announce(client, s.comboEnabled ? "Shield Mace Combo: ON" : "Shield Mace Combo: OFF");
    }

    public void toggleBreachSwap(MinecraftClient client) {
        s.breachSwapEnabled = !s.breachSwapEnabled;
        resetRuntimeState();
        announce(client, s.breachSwapEnabled ? "Breach Mace Swap: ON" : "Breach Mace Swap: OFF");
    }

    public void toggleMaceSpam(MinecraftClient client) {
        s.maceSpamEnabled = !s.maceSpamEnabled;
        resetRuntimeState();
        announce(client, s.maceSpamEnabled ? "Mace Spam: ON" : "Mace Spam: OFF");
    }

    public void toggleSilentAim(MinecraftClient client) {
        s.silentAimEnabled = !s.silentAimEnabled;
        announce(client, s.silentAimEnabled ? "Silent Aim: ON" : "Silent Aim: OFF");
    }

    public void toggleHeightSmash(MinecraftClient client) {
        s.heightSmashEnabled = !s.heightSmashEnabled;
        announce(client, s.heightSmashEnabled ? "Height Smash: ON" : "Height Smash: OFF");
    }

    public void toggleHitboxExpand(MinecraftClient client) {
        s.hitboxExpandEnabled = !s.hitboxExpandEnabled;
        announce(client, s.hitboxExpandEnabled ? "Hitbox Expander: ON" : "Hitbox Expander: OFF");
    }

    public void toggleAutoTotem(MinecraftClient client) {
        s.autoTotemEnabled = !s.autoTotemEnabled;
        autoTotemCooldown = 0;
        announce(client, s.autoTotemEnabled ? "Auto Totem: ON" : "Auto Totem: OFF");
    }

    public void toggleNoFall(MinecraftClient client) {
        s.noFallEnabled = !s.noFallEnabled;
        announce(client, s.noFallEnabled ? "No Fall: ON" : "No Fall: OFF");
    }

    public void toggleKillAura(MinecraftClient client) {
        s.killAuraEnabled = !s.killAuraEnabled;
        killAuraCooldown = 0;
        announce(client, s.killAuraEnabled ? "Kill Aura: ON" : "Kill Aura: OFF");
    }

    public void toggleFlight(MinecraftClient client) {
        s.flightEnabled = !s.flightEnabled;
        if (client.player != null) {
            var abilities = client.player.getAbilities();
            if (s.flightEnabled) {
                abilities.allowFlying = true;
                abilities.setFlySpeed(Math.max(1, Math.min(50, s.flightSpeedTenths)) * 0.01f);
            } else {
                // Restore vanilla state — only creative players keep flight.
                abilities.allowFlying = client.player.isCreative();
                abilities.flying = abilities.allowFlying && abilities.flying;
                abilities.setFlySpeed(0.05f);
            }
        }
        announce(client, s.flightEnabled ? "Flight: ON (double-tap space)" : "Flight: OFF");
    }

    public void resetRuntimeState() {
        state                    = State.IDLE;
        delayTimer               = 0;
        cooldownTimer            = 0;
        wasAttackPressed         = false;
        smartFallTicksSinceClick = 0;
    }

    private void announce(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        // ── Auto Totem runs independently of the other features ─────────────
        if (s.autoTotemEnabled) {
            tickAutoTotem(client);
        }

        // ── Flight is a continuous "force" — re-applied every tick so the
        //     server's ability sync packets can't take it away mid-flight ──
        if (s.flightEnabled) {
            tickFlight(client);
        }

        // ── Kill Aura runs independently of the combo / spam state machine ─
        if (s.killAuraEnabled) {
            tickKillAura(client);
        }

        // ── Silent Aim runs independently of the other features ──────────────
        if (s.silentAimEnabled) {
            tickSilentAim(client);
        }

        // ── Click edge-detection (observe only — does NOT consume the key event)
        //     Tracked every tick so Height Smash can fire even when no other
        //     combo/breach/spam features are on. ──
        boolean isAttackPressed = client.options.attackKey.isPressed();
        boolean clickedThisTick = isAttackPressed && !wasAttackPressed;
        wasAttackPressed = isAttackPressed;

        // ── Feature 6 (height smash): fires once per click while holding a mace ──
        if (s.heightSmashEnabled && clickedThisTick) {
            tryHeightSmash(client);
        }

        if (!s.comboEnabled && !s.breachSwapEnabled && !s.maceSpamEnabled) return;

        // ── Feature 4 (mace spam): runs every tick, independent of state machine ──
        if (s.maceSpamEnabled) {
            tickMaceSpam(client);
        }

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
            case SHIELD_BREAK_AXE_SWUNG  -> handleSwapBackReturn(client, s.comboCooldownTicks);
            case BREACH_SWAP_RETURN      -> handleSwapBackReturn(client, s.breachSwapCooldownTicks);
        }
    }

    // ── Idle: breach-swap → click-on-shield → auto-combo ──────────────────────

    private void handleIdle(MinecraftClient client, boolean clickedThisTick) {
        // Priority 1: breach-mace swap on any attack against a mob or player
        if (s.breachSwapEnabled && clickedThisTick) {
            LivingEntity livingTarget = getLookedAtLiving(client);
            if (livingTarget != null) {
                int breachSlot  = findBreachMaceSlot(client);
                int currentSlot = client.player.getInventory().getSelectedSlot();

                if (breachSlot != -1 && breachSlot != currentSlot) {
                    previousSlot = currentSlot;
                    client.player.getInventory().setSelectedSlot(breachSlot);
                    client.interactionManager.attackEntity(client.player, livingTarget);
                    client.player.swingHand(Hand.MAIN_HAND);

                    state      = State.BREACH_SWAP_RETURN;
                    delayTimer = s.breachSwapDelayTicks;
                    return;
                }
            }
        }

        // Features 1 & 2 are gated by the combo toggle
        if (!s.comboEnabled) return;

        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) return;

        // Priority 2: if the user clicked AND target is actively blocking → axe swap-and-return
        if (clickedThisTick && target.isBlocking()) {
            int axeSlot = findAxeSlot(client);
            if (axeSlot != -1) {
                previousSlot = client.player.getInventory().getSelectedSlot();
                client.player.getInventory().setSelectedSlot(axeSlot);
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);

                state      = State.SHIELD_BREAK_AXE_SWUNG;
                delayTimer = s.comboSwapDelayTicks;
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
        delayTimer = s.comboSwapDelayTicks;
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
        cooldownTimer = s.comboCooldownTicks;
    }

    // ── Click-shield-break / breach-swap: after delay swap back to previous slot ──

    private void handleSwapBackReturn(MinecraftClient client, int cooldownTicks) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        client.player.getInventory().setSelectedSlot(previousSlot);

        state         = State.IDLE;
        cooldownTimer = cooldownTicks;
    }

    // ── Feature 4: mace spam (N clicks per tick on a player target) ───────────

    private static final double MACE_BASE_DAMAGE      = 5.0;   // approximate base attack damage
    private static final double SHIELD_BASE_DURABILITY = 336.0;
    private static final double UNBREAKING_3_FACTOR   = 4.0;   // expected dur usage divisor (lvl+1)
    private static final double SMART_TARGET_FD       = 3.0;   // optimal fd per click (end of 4-dmg/block zone)
    private static final double SMART_MIN_FD          = 1.5;   // minimum fd to actually trigger smash

    private int smartFallTicksSinceClick = 0;

    private void tickMaceSpam(MinecraftClient client) {
        ItemStack mainHand = client.player.getInventory().getStack(
                client.player.getInventory().getSelectedSlot());
        if (!(mainHand.getItem() instanceof MaceItem)) return;

        if (s.maceSpamSmartFallClick) {
            tickSmartFallClick(client);
        } else {
            // Original behavior: N clicks per tick at a player target
            PlayerEntity target = getLookedAtPlayer(client);
            if (target == null) return;
            int n = Math.max(1, s.maceSpamClicksPerTick);
            for (int i = 0; i < n; i++) {
                client.interactionManager.attackEntity(client.player, target);
            }
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void tickSmartFallClick(MinecraftClient client) {
        // Reset cadence when on the ground — fresh fall coming
        if (client.player.isOnGround()) {
            smartFallTicksSinceClick = 0;
            return;
        }

        double fd = client.player.fallDistance;
        if (fd <= 0) return;

        PlayerEntity target = getLookedAtPlayer(client);
        if (target == null) {
            smartFallTicksSinceClick++;
            return;
        }

        // Current downward speed (positive when falling)
        double fallSpeed = -client.player.getVelocity().y;
        if (fallSpeed < 0.05) {
            // Going up or barely moving — no smash possible
            smartFallTicksSinceClick++;
            return;
        }

        // Time (in ticks) for fd to grow back to SMART_TARGET_FD after a smash reset
        int interval = Math.max(1, (int) Math.ceil(SMART_TARGET_FD / fallSpeed));

        smartFallTicksSinceClick++;

        // Estimated smash damage at SMART_TARGET_FD (mace bonus = 4*min(fd,3))
        double smashDmg = MACE_BASE_DAMAGE + 4.0 * Math.min(SMART_TARGET_FD, 3.0);
        double expectedDurPerHit = smashDmg / UNBREAKING_3_FACTOR;
        int hitsToBreak = (int) Math.ceil(SHIELD_BASE_DURABILITY / Math.max(0.1, expectedDurPerHit));

        // Live action-bar readout so the player can see the calculation
        String msg = String.format(
                "Smart Fall: fd=%.1f  v=%.2f b/t  click every %d t  ~%d hits to break U3 shield",
                fd, fallSpeed, interval, hitsToBreak);
        client.player.sendMessage(Text.literal(msg), true);

        // Click only if enough ticks have passed AND fd is actually past the smash threshold
        if (smartFallTicksSinceClick >= interval && fd >= SMART_MIN_FD) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
            smartFallTicksSinceClick = 0;
        }
    }

    // ── Feature 10: kill aura ────────────────────────────────────────────────
    // Every `killAuraDelayTicks` ticks, find the closest entity inside
    // `killAuraRangeBlocks` that matches the configured target classes
    // (players / hostile mobs / passive mobs) and attack it. Distance is
    // measured to the entity's bounding-box edge so the range matches the
    // server's own reach check.
    private void tickKillAura(MinecraftClient client) {
        if (killAuraCooldown > 0) {
            killAuraCooldown--;
            return;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        double range = Math.max(1, Math.min(8, s.killAuraRangeBlocks));
        Vec3d eye = client.player.getEyePos();
        Box searchBox = Box.of(eye, range * 2, range * 2, range * 2);

        List<LivingEntity> candidates = client.world.getEntitiesByClass(
                LivingEntity.class, searchBox, e -> {
                    if (e == client.player || !e.isAlive()) return false;
                    if (e instanceof PlayerEntity p) {
                        if (p.isSpectator()) return false;
                        if (p.isCreative()) return false;
                        return s.killAuraTargetPlayers;
                    }
                    if (e instanceof HostileEntity) return s.killAuraTargetHostile;
                    if (e instanceof PassiveEntity) return s.killAuraTargetPassive;
                    return false;
                });

        LivingEntity best = null;
        double bestDistSq = range * range;
        for (LivingEntity e : candidates) {
            // Use bounding-box distance from the eye so the range check
            // mirrors what the server does on attack.
            double distSq = e.getBoundingBox().squaredMagnitude(eye);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = e;
            }
        }
        if (best == null) return;

        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
        killAuraCooldown = Math.max(1, s.killAuraDelayTicks);
    }

    // ── Feature 11: flight ────────────────────────────────────────────────────
    // Each tick, force the local player's allowFlying ability on and re-apply
    // the configured fly-speed. The server may overwrite these via its own
    // PlayerAbilitiesS2CPacket, so re-applying every tick keeps flight active
    // until the user disables the toggle. The user takes off / lands by
    // double-tapping space, exactly like vanilla creative mode.
    private void tickFlight(MinecraftClient client) {
        if (client.player == null) return;
        var abilities = client.player.getAbilities();
        abilities.allowFlying = true;
        abilities.setFlySpeed(Math.max(1, Math.min(50, s.flightSpeedTenths)) * 0.01f);
    }

    // ── Feature 8: auto totem ────────────────────────────────────────────────
    // Every `autoTotemDelayTicks` ticks, if the offhand isn't already a Totem
    // of Undying, scan the hotbar + main inventory for one and send a slot
    // swap (button 40 = offhand swap key) so it ends up in the offhand. Any
    // item previously in the offhand goes back to the totem's old slot.
    private void tickAutoTotem(MinecraftClient client) {
        if (autoTotemCooldown > 0) {
            autoTotemCooldown--;
            return;
        }
        if (client.player == null || client.interactionManager == null) return;

        ItemStack offhand = client.player.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        var inv = client.player.getInventory();
        int totemInvIndex = -1;
        // PlayerInventory: 0..8 hotbar, 9..35 main. Skip armor (36..39) and offhand (40).
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                totemInvIndex = i;
                break;
            }
        }
        if (totemInvIndex < 0) return;

        // Map PlayerInventory index → PlayerScreenHandler slot id.
        // Hotbar (0..8) lives at screen slots 36..44; main inventory (9..35)
        // maps 1:1 (slot ids 9..35).
        int screenSlot = (totemInvIndex < 9) ? totemInvIndex + 36 : totemInvIndex;

        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                screenSlot,
                40,                       // 40 = offhand swap key (matches F key default)
                SlotActionType.SWAP,
                client.player);

        autoTotemCooldown = Math.max(1, s.autoTotemDelayTicks);
    }

    // ── Feature 6: height smash ──────────────────────────────────────────────
    // Sends a burst of fake position packets that drop the server-tracked Y by
    // (heightSmashPackets × heightSmashDropPerPacket) blocks with onGround=false,
    // so the server accumulates a large fall-distance. Then sends matching
    // upward packets to restore Y to the original (so the server's reach check
    // on the attack still passes) and finally sends the attack — at which point
    // the mace's smash bonus is applied at the inflated fall-distance.
    //
    // NOTE: the player's server-side fall-distance remains high after this
    // burst, so when the client's normal movement packets next report
    // onGround=true, the server applies that fall damage to the attacker.
    // Use with high HP / Resistance / lots of armor.
    private void tryHeightSmash(MinecraftClient client) {
        var player = client.player;
        if (player == null || player.networkHandler == null) return;

        // Must be holding a mace
        ItemStack mainHand = player.getInventory().getStack(
                player.getInventory().getSelectedSlot());
        if (!(mainHand.getItem() instanceof MaceItem)) return;

        // Must be looking at a living target
        LivingEntity target = getLookedAtLiving(client);
        if (target == null) return;

        int packets = Math.max(1, Math.min(40, s.heightSmashPackets));
        double dropPerPacket = Math.max(1, Math.min(9, s.heightSmashDropPerPacket));

        double origX = player.getX();
        double origY = player.getY();
        double origZ = player.getZ();

        var net = player.networkHandler;

        // Phase 1: drop server-tracked Y in small steps, accumulating fall-distance.
        double fakeY = origY;
        for (int i = 0; i < packets; i++) {
            fakeY -= dropPerPacket;
            net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    origX, fakeY, origZ, false, false));
        }

        // Phase 2: rise back to the original Y so the attack's reach check passes.
        // onGround=false the whole way so the server keeps the accumulated
        // fall-distance instead of resetting it on landing.
        for (int i = 0; i < packets; i++) {
            fakeY += dropPerPacket;
            net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    origX, fakeY, origZ, false, false));
        }

        // Phase 3: do the actual attack with the mace.
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        double fakeFall = packets * dropPerPacket;
        client.player.sendMessage(
                Text.literal(String.format("Height Smash: faked fd ≈ %.0f blocks", fakeFall)),
                true);
    }

    // ── Feature 5: silent aim (soft camera assist when crosshair is close) ───

    private void tickSilentAim(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();

        double maxRange    = Math.max(1, s.silentAimRangeBlocks);
        double maxAngleDeg = Math.max(1, s.silentAimMaxAngleDegrees);
        double strength    = Math.max(0.01, s.silentAimStrengthPct / 100.0);

        // Reject any target whose direction makes a wider cone with the
        // current look vector than the configured max angle. Cosine
        // comparison avoids per-target acos() calls.
        double minCos = Math.cos(Math.toRadians(maxAngleDeg));

        Vec3d look = client.player.getRotationVector();

        Box searchBox = Box.of(eye, maxRange * 2, maxRange * 2, maxRange * 2);
        List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                PlayerEntity.class, searchBox,
                p -> p != client.player && p.isAlive() && !p.isSpectator());

        PlayerEntity bestTarget = null;
        Vec3d        bestAimPoint = null;
        double       bestScore = -1.0;   // higher cos = closer to crosshair

        for (PlayerEntity p : nearby) {
            Vec3d aimPoint = p.getEyePos();             // aim at the head
            Vec3d toTarget = aimPoint.subtract(eye);
            double dist = toTarget.length();
            if (dist < 0.01 || dist > maxRange) continue;

            double cos = look.dotProduct(toTarget.multiply(1.0 / dist));
            if (cos < minCos) continue;                 // outside the cone

            // Pick the target most aligned with the crosshair (tie-break by
            // proximity by adding a small distance penalty).
            double score = cos - dist * 0.001;
            if (score > bestScore) {
                bestScore    = score;
                bestTarget   = p;
                bestAimPoint = aimPoint;
            }
        }

        if (bestTarget == null) return;

        Vec3d delta = bestAimPoint.subtract(eye);
        double targetYaw   = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double horiz       = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double targetPitch = -Math.toDegrees(Math.atan2(delta.y, horiz));

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        double yawDelta   = MathHelper.wrapDegrees(targetYaw - curYaw);
        double pitchDelta = targetPitch - curPitch;

        client.player.setYaw  ((float) (curYaw   + yawDelta   * strength));
        client.player.setPitch((float) (curPitch + pitchDelta * strength));
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
