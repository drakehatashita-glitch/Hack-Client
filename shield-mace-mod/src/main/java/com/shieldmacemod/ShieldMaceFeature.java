package com.shieldmacemod;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    // ── Misc rate-limiters / edge state for the simple toggles ──
    private int triggerBotCooldown     = 0;
    private double autoClickerAccumulator = 0; // fractional clicks per tick
    private int autoArmorCooldown      = 0;
    private int chestStealerCooldown   = 0;
    private boolean longJumpJustJumped = false;
    private boolean wasJumpPressed     = false;
    private boolean stepHeightApplied  = false;
    private boolean reachApplied       = false;
    private double  appliedReachVal    = 0;
    private double  appliedStepVal     = 0;
    private Double  savedGamma         = null;

    // ── Blink state ──────────────────────────────────────────────────────
    // While blinkEnabled, every outgoing PlayerMoveC2SPacket is intercepted
    // by ClientPlayNetworkHandlerMixin and pushed onto this queue instead
    // of being sent to the server. When blink is turned OFF, the queue is
    // drained in FIFO order back through the network handler so the server
    // fast-forwards the player's tracked position to where they actually
    // are now. `blinkBypass` is set to true while we are flushing so the
    // mixin lets those replays through instead of re-queuing them.
    private final Deque<Packet<?>> blinkQueue = new ArrayDeque<>();
    private boolean blinkBypass = false;

    public void toggleCombo(MinecraftClient client) {
        s.comboEnabled = !s.comboEnabled;
        resetRuntimeState();
        announce(client, s.comboEnabled ? "Auto Stun Slam: ON" : "Auto Stun Slam: OFF");
    }

    public void toggleBreachSwap(MinecraftClient client) {
        s.breachSwapEnabled = !s.breachSwapEnabled;
        resetRuntimeState();
        announce(client, s.breachSwapEnabled ? "Breach Swap: ON" : "Breach Swap: OFF");
    }

    public void toggleMaceSpam(MinecraftClient client) {
        s.maceSpamEnabled = !s.maceSpamEnabled;
        resetRuntimeState();
        announce(client, s.maceSpamEnabled ? "Shield Breaker: ON" : "Shield Breaker: OFF");
    }

    public void toggleSilentAim(MinecraftClient client) {
        s.silentAimEnabled = !s.silentAimEnabled;
        announce(client, s.silentAimEnabled ? "Silent Aim: ON" : "Silent Aim: OFF");
    }

    public void toggleHeightSmash(MinecraftClient client) {
        s.heightSmashEnabled = !s.heightSmashEnabled;
        announce(client, s.heightSmashEnabled ? "Mace Kill: ON" : "Mace Kill: OFF");
    }

    public void toggleHitboxExpand(MinecraftClient client) {
        s.hitboxExpandEnabled = !s.hitboxExpandEnabled;
        announce(client, s.hitboxExpandEnabled ? "Hitboxes: ON" : "Hitboxes: OFF");
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

    public void toggleBlink(MinecraftClient client) {
        s.blinkEnabled = !s.blinkEnabled;
        if (!s.blinkEnabled) {
            // Disabling: flush every queued movement packet so the server
            // fast-forwards the player's tracked position to where they
            // actually are now. The bypass flag prevents the outgoing
            // mixin from re-queuing the packets we are replaying here.
            int flushed = blinkQueue.size();
            if (client.player != null && client.player.networkHandler != null) {
                blinkBypass = true;
                try {
                    while (!blinkQueue.isEmpty()) {
                        client.player.networkHandler.sendPacket(blinkQueue.pollFirst());
                    }
                } finally {
                    blinkBypass = false;
                }
            } else {
                blinkQueue.clear();
            }
            announce(client, "Blink: OFF (flushed " + flushed + " packets)");
        } else {
            blinkQueue.clear();
            announce(client, "Blink: ON");
        }
    }

    /** Called from {@code ClientPlayNetworkHandlerMixin}. Returns true if
     *  the packet was queued and the original send should be cancelled. */
    public boolean queueBlinkPacket(Packet<?> packet) {
        if (!s.blinkEnabled || blinkBypass) return false;
        if (!(packet instanceof PlayerMoveC2SPacket)) return false;
        // Cap the queue so a player who leaves blink on for hours doesn't
        // OOM the client. 24000 packets ≈ 20 minutes of movement at 20 tps.
        if (blinkQueue.size() >= 24000) return false;
        blinkQueue.addLast(packet);
        return true;
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

    /**
     * Generic toggle used by the keybind handler in {@link ShieldMaceMod} for
     * features whose only action is "flip a boolean and tell the player". The
     * caller passes a {@link Runnable} that flips the setting (so we don't
     * have to know which field) and a {@link BooleanSupplier} that reads the
     * new value back so we can show ON/OFF in chat.
     */
    public void toggleSimple(MinecraftClient client, String name,
                              Runnable toggle, BooleanSupplier reader) {
        toggle.run();
        announce(client, name + ": " + (reader.getAsBoolean() ? "ON" : "OFF"));
    }

    public void resetRuntimeState() {
        state                    = State.IDLE;
        delayTimer               = 0;
        cooldownTimer            = 0;
        wasAttackPressed         = false;
        smartFallTicksSinceClick = 0;
        triggerBotCooldown       = 0;
        autoClickerAccumulator   = 0;
        autoArmorCooldown        = 0;
        chestStealerCooldown     = 0;
        longJumpJustJumped       = false;
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

        // ── Movement / combat / assist ticks (always evaluated; cheap when off)
        tickMovement(client);
        tickReach(client);
        tickFullbright(client);
        if (s.triggerBotEnabled)   tickTriggerBot(client);
        if (s.autoClickerEnabled)  tickAutoClicker(client);
        if (s.autoArmorEnabled)    tickAutoArmor(client);
        if (s.chestStealerEnabled) tickChestStealer(client);

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

        double rangeSq = range * range;

        if (s.killAuraTargetAll) {
            // Multi-target: attack every valid entity inside the range
            // this tick, then start the cooldown so we don't hammer the
            // server every single tick.
            int hits = 0;
            for (LivingEntity e : candidates) {
                double distSq = e.getBoundingBox().squaredMagnitude(eye);
                if (distSq > rangeSq) continue;
                client.interactionManager.attackEntity(client.player, e);
                hits++;
            }
            if (hits > 0) {
                client.player.swingHand(Hand.MAIN_HAND);
                killAuraCooldown = Math.max(1, s.killAuraDelayTicks);
            }
            return;
        }

        LivingEntity best = null;
        double bestDistSq = rangeSq;
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
                Text.literal(String.format("Mace Kill: faked fd ≈ %.0f blocks", fakeFall)),
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

    // ─────────────────────────────────────────────────────────────────────────
    // Movement features (Speed / Sprint / Step / LongJump / Jesus / Spider / Glide)
    // ─────────────────────────────────────────────────────────────────────────
    private void tickMovement(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) return;

        // ── Step (raise STEP_HEIGHT attribute) ──
        EntityAttributeInstance stepAttr = p.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (stepAttr != null) {
            if (s.stepEnabled) {
                double want = Math.max(1, Math.min(30, s.stepHeightTenths)) / 10.0;
                if (!stepHeightApplied || want != appliedStepVal) {
                    stepAttr.setBaseValue(want);
                    appliedStepVal    = want;
                    stepHeightApplied = true;
                }
            } else if (stepHeightApplied) {
                // Vanilla default is 0.6 blocks for players
                stepAttr.setBaseValue(0.6);
                stepHeightApplied = false;
            }
        }

        // ── Sprint (force sprint while moving forward) ──
        if (s.sprintEnabled && p.input != null && p.input.playerInput != null
                && p.input.playerInput.forward()
                && !p.isUsingItem() && !p.horizontalCollision
                && p.getHungerManager().getFoodLevel() > 0) {
            p.setSprinting(true);
        }

        Vec3d v = p.getVelocity();
        double vx = v.x, vy = v.y, vz = v.z;

        // ── Speed (multiply horizontal velocity) ──
        if (s.speedEnabled && p.input != null && p.input.playerInput != null) {
            double mult = Math.max(11, Math.min(50, s.speedMultiplierTenths)) / 10.0;
            var pi = p.input.playerInput;
            boolean moving = pi.forward() || pi.backward() || pi.left() || pi.right();
            if (moving) {
                vx *= mult;
                vz *= mult;
            }
        }

        // ── Long Jump (boost horizontal velocity on jump) ──
        boolean jumpPressed = p.input != null && p.input.playerInput != null && p.input.playerInput.jump();
        if (s.longJumpEnabled && jumpPressed && !wasJumpPressed
                && p.isOnGround() && !longJumpJustJumped) {
            double mult = Math.max(11, Math.min(40, s.longJumpMultiplierTenths)) / 10.0;
            vx *= mult;
            vz *= mult;
            longJumpJustJumped = true;
        }
        if (p.isOnGround() && !jumpPressed) {
            longJumpJustJumped = false;
        }
        wasJumpPressed = jumpPressed;

        // ── Glide (clamp downward velocity) ──
        if (s.glideEnabled && vy < 0) {
            double cap = -Math.max(1, Math.min(50, s.glideFallSpeedTenths)) / 100.0;
            if (vy < cap) vy = cap;
            // Cancel the accumulated fall distance so we don't take damage on landing
            p.fallDistance = 0;
        }

        // ── Jesus (walk on liquids) ──
        if (s.jesusEnabled && !jumpPressed) {
            BlockPos below = BlockPos.ofFloored(p.getX(), p.getY() - 0.05, p.getZ());
            FluidState fluid = client.world.getFluidState(below);
            if (!fluid.isEmpty()) {
                if (vy < 0) vy = 0;
                p.setOnGround(true);
                p.fallDistance = 0;
            }
        }

        // ── Spider (climb walls on horizontal collision) ──
        if (s.spiderEnabled && p.horizontalCollision) {
            double climb = Math.max(1, Math.min(40, s.spiderClimbTenths)) / 100.0;
            if (vy < climb) vy = climb;
        }

        if (vx != v.x || vy != v.y || vz != v.z) {
            p.setVelocity(vx, vy, vz);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reach (set the player's entity-interaction-range attribute)
    // ─────────────────────────────────────────────────────────────────────────
    private void tickReach(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) return;

        EntityAttributeInstance attr =
                p.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (attr == null) return;

        if (s.reachEnabled) {
            double want = Math.max(30, Math.min(80, s.reachBlocksTenths)) / 10.0;
            if (!reachApplied || want != appliedReachVal) {
                attr.setBaseValue(want);
                appliedReachVal = want;
                reachApplied    = true;
            }
        } else if (reachApplied) {
            attr.setBaseValue(3.0);   // vanilla default
            reachApplied = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fullbright — force gamma to 100 while enabled, restore on disable.
    // The lightmap mixin in BackgroundRendererMixin handles fog brightening.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickFullbright(MinecraftClient client) {
        try {
            var gammaOpt = client.options.getGamma();
            if (s.fullbrightEnabled) {
                if (savedGamma == null) {
                    savedGamma = gammaOpt.getValue();
                }
                if (gammaOpt.getValue() < 15.0) {
                    gammaOpt.setValue(100.0);
                }
            } else if (savedGamma != null) {
                gammaOpt.setValue(savedGamma);
                savedGamma = null;
            }
        } catch (Throwable ignored) {
            // SimpleOption.setValue may clamp via its validator; ignore.
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TriggerBot — attack the entity directly under the crosshair
    // ─────────────────────────────────────────────────────────────────────────
    private void tickTriggerBot(MinecraftClient client) {
        if (triggerBotCooldown > 0) { triggerBotCooldown--; return; }
        LivingEntity target = getLookedAtLiving(client);
        if (target == null) return;
        if (target instanceof PlayerEntity pe && (pe.isCreative() || pe.isSpectator())) return;
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        triggerBotCooldown = Math.max(1, s.triggerBotDelayTicks);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AutoClicker — while the attack key is held, fire attacks at N CPS
    // ─────────────────────────────────────────────────────────────────────────
    private void tickAutoClicker(MinecraftClient client) {
        if (!client.options.attackKey.isPressed()) {
            autoClickerAccumulator = 0;
            return;
        }
        double cps = Math.max(1, Math.min(40, s.autoClickerCps));
        // 20 ticks/sec → clicks per tick = cps / 20
        autoClickerAccumulator += cps / 20.0;
        while (autoClickerAccumulator >= 1.0) {
            autoClickerAccumulator -= 1.0;
            LivingEntity target = getLookedAtLiving(client);
            if (target != null) {
                client.interactionManager.attackEntity(client.player, target);
            }
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AutoArmor — equip the strongest helmet/chest/legs/boots from inventory
    // ─────────────────────────────────────────────────────────────────────────
    private void tickAutoArmor(MinecraftClient client) {
        if (autoArmorCooldown > 0) { autoArmorCooldown--; return; }
        if (client.player.currentScreenHandler != client.player.playerScreenHandler) return;

        ClientPlayerEntity p = client.player;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {

            ItemStack worn = p.getEquippedStack(slot);
            int wornScore  = armorScore(worn, slot);
            int bestIndex  = -1;
            int bestScore  = wornScore;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = p.getInventory().getStack(i);
                int score = armorScore(stack, slot);
                if (score > bestScore) { bestScore = score; bestIndex = i; }
            }

            if (bestIndex >= 0) {
                // Slot id for the armor in the player screen handler:
                //   HEAD=5, CHEST=6, LEGS=7, FEET=8
                int armorSlotId = switch (slot) {
                    case HEAD  -> 5;
                    case CHEST -> 6;
                    case LEGS  -> 7;
                    case FEET  -> 8;
                    default    -> -1;
                };
                if (armorSlotId < 0) continue;

                int sourceSlotId = (bestIndex < 9) ? bestIndex + 36 : bestIndex;
                int syncId = p.playerScreenHandler.syncId;

                // Pickup new armor → place into armor slot → put any displaced
                // armor back where the new piece used to be.
                client.interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, p);
                client.interactionManager.clickSlot(syncId, armorSlotId,  0, SlotActionType.PICKUP, p);
                if (!p.playerScreenHandler.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, p);
                }
                autoArmorCooldown = Math.max(1, s.autoArmorDelayTicks);
                return; // one piece per tick to stay polite
            }
        }

        autoArmorCooldown = Math.max(1, s.autoArmorDelayTicks);
    }

    private static int armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        // 1.21.11 removed the public ArmorItem class entirely — any item
        // can be equipment as long as it carries an EQUIPPABLE component.
        // We use that component's slot to filter, then approximate the
        // armor tier with the stack's max-damage (durability), which
        // tracks tier ordering (leather < golden < chain < iron < diamond < netherite).
        var eq = stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return 0;
        int dur = stack.getMaxDamage();
        return Math.max(1, dur);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ChestStealer — shift-click everything out of any opened container
    // ─────────────────────────────────────────────────────────────────────────
    private void tickChestStealer(MinecraftClient client) {
        if (chestStealerCooldown > 0) { chestStealerCooldown--; return; }
        ScreenHandler sh = client.player.currentScreenHandler;
        if (sh == client.player.playerScreenHandler) return;
        if (!(sh instanceof GenericContainerScreenHandler)
                && !(sh instanceof ShulkerBoxScreenHandler)) return;

        // The container's own slots come first; the player inventory is
        // appended after them. Shift-click container slots only.
        int containerSlots;
        if (sh instanceof GenericContainerScreenHandler g) {
            containerSlots = g.getRows() * 9;
        } else {
            containerSlots = 27; // shulker
        }

        for (int i = 0; i < containerSlots && i < sh.slots.size(); i++) {
            Slot slot = sh.slots.get(i);
            if (!slot.hasStack()) continue;
            client.interactionManager.clickSlot(
                    sh.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
            chestStealerCooldown = Math.max(1, s.chestStealerDelayTicks);
            return; // one slot per tick interval
        }
    }

    // ── Block-classification helper used by the X-Ray render hook ────────────
    public static boolean isOreBlock(Block block) {
        return block == Blocks.COAL_ORE          || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.IRON_ORE          || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.COPPER_ORE        || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.GOLD_ORE          || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.REDSTONE_ORE      || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.LAPIS_ORE         || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.DIAMOND_ORE       || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.EMERALD_ORE       || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.NETHER_GOLD_ORE   || block == Blocks.NETHER_QUARTZ_ORE
            || block == Blocks.ANCIENT_DEBRIS;
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
