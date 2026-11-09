package dev.tentaworks.rig;

import com.simibubi.create.AllItems;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.network.FarUsePacket;
import dev.tentaworks.registry.TentaworksLang;
import dev.tentaworks.rig.RigSettings.FarUseMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Right Actuator's Far Use, server side (adr/0007). While it may act ({@link #lifts}), transient modifiers lift the
 * wearer's block and entity interaction ranges to the Rig's Reach, so vanilla's own click paths reach that far; vanilla syncs both attributes to every client, so the wearer's crosshair matches. A target past arm's
 * length ({@link #beyond}, the range without the lift) must also be in sight ({@link WorkSight}), may be a player only with
 * {@code farUse.players}, costs Air and is told to the clients ({@link FarUsePacket}).
 * <p>
 * Drops and XP of a block broken past arm's length go to the wearer: {@link BlockDropsEvent} hands over the item entities
 * and the XP before they enter the world, so they are moved to the wearer there, with no capture of spawned entities.
 * Ship blocks (plot coordinates) are left to vanilla and Sable.
 */
public final class FarUse {
    public static final ResourceLocation REACH_ID = TentaworksIds.id(TentaworksIds.FAR_USE_REACH);
    public static final ResourceLocation ENTITY_ID = TentaworksIds.id(TentaworksIds.FAR_USE_ENTITY);
    public static final String NO_SIGHT = TentaworksLang.refusalKey(TentaworksLang.FAR_USE, "no_sight");
    public static final String PLAYERS = TentaworksLang.refusalKey(TentaworksLang.FAR_USE, "players");
    /** Ticks a {@link FarUsePacket} keeps the Right Actuator at its target. */
    public static final int TTL = 10;
    static final int CHARGE_GAP = 1;
    static final int PACKET_GAP = 2;
    static final int REFUSAL_GAP = 20;
    private static final double SLACK = 1.0;
    private static final double EPS = 1.0E-6;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    /** Where a target lies for one interaction range. */
    public enum Far {
        /** No lift on this range, or within arm's length: vanilla rules alone. */
        NEAR,
        /** Past arm's length, within the lifted range: a Far Use. */
        FAR,
        /** Past the lifted range too: vanilla refuses it anyway. */
        OUT
    }

    /**
     * Server ticks of the wearer's last charge, packet and refusal (one click fires several events: both hands, interact-at and
     * interact), and the block being mined past arm's length (null none).
     */
    private static final class Track {
        int charged = Integer.MIN_VALUE / 2;
        int sent = Integer.MIN_VALUE / 2;
        int refused = Integer.MIN_VALUE / 2;
        String refusal = "";
        @Nullable BlockPos mining;
    }

    private FarUse() {
    }

    /** The Right Actuator lends either hand its Reach: fitted, allowed by the Rig Setting now, no Extendo Grip in hand, no heavy hold, not limp. */
    public static boolean lifts(boolean fitted, FarUseMode mode, boolean rigOut, boolean extendo, boolean heavy, boolean limp) {
        if (!fitted || extendo || heavy || limp) return false;
        return switch (mode) {
            case ALWAYS -> true;
            case RIG_OUT -> rigOut;
            case OFF -> false;
        };
    }

    /** The modifier that lifts a range of {@code without} (the value without the lift) to {@code target}; it never lowers a range. */
    public static double amount(double target, double without) {
        return Math.max(0, target - without);
    }

    /** A target {@code distSqr} away (squared, from the eye to its box) is past arm's length: farther than {@code base}, the range without the lift. */
    public static boolean beyond(double distSqr, double base) {
        return distSqr > base * base;
    }

    /** Where a target {@code distSqr} away lies when the range is {@code base} without the lift and {@code range} with it. */
    public static Far far(double distSqr, double base, double range) {
        if (range <= base + EPS || !beyond(distSqr, base)) return Far.NEAR;
        double r = range + SLACK;
        return distSqr < r * r ? Far.FAR : Far.OUT;
    }

    /** At least {@code gap} ticks passed since {@code last}. */
    static boolean due(int last, int now, int gap) {
        return now - last >= gap;
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) tick(p);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, FarUse::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onInteractAt);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onAttack);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarUse::onDrops);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> TRACKS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TRACKS.clear());
    }

    /** {@link #lifts} for this wearer now. "Rig out" is any state but Stowed ({@link RigSide#deployed}). */
    public static boolean lifting(ServerPlayer player) {
        ItemStack rig = WornRig.of(player).stack();
        return lifts(Actuators.fitted(rig), RigSettings.of(rig).farUse(), RigSide.deployed(player), extendo(player), Grab.heavy(player),
                Actuators.limp(player));
    }

    /** A Create Extendo Grip in either hand: Create lifts the range itself, the Actuator idles. */
    public static boolean extendo(Player player) {
        return AllItems.EXTENDO_GRIP.isIn(player.getMainHandItem()) || AllItems.EXTENDO_GRIP.isIn(player.getOffhandItem());
    }

    static void tick(ServerPlayer player) {
        boolean on = lifting(player);
        double reach = on ? TentacleRigItem.reach(player) : 0;
        lift(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), REACH_ID, reach);
        lift(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), ENTITY_ID, reach);
        keepMining(player, on);
    }

    /** While a far block is mined the Right Actuator stays at it: the {@link FarUsePacket} is told again before its ttl runs out. */
    private static void keepMining(ServerPlayer player, boolean on) {
        Track t = TRACKS.get(player.getUUID());
        if (t == null || t.mining == null) return;
        if (!on || player.level().getBlockState(t.mining).isAir()) {
            t.mining = null;
            return;
        }
        int now = player.server.getTickCount();
        if (!due(t.sent, now, TTL - PACKET_GAP)) return;
        t.sent = now;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FarUsePacket(player.getId(), Vec3.atCenterOf(t.mining), FarUsePacket.Kind.BLOCK, TTL));
    }

    private static void lift(@Nullable AttributeInstance inst, ResourceLocation id, double target) {
        if (inst == null) return;
        AttributeModifier m = inst.getModifier(id);
        double add = amount(target, inst.getValue() - (m == null ? 0 : m.amount()));
        if (add <= EPS) {
            if (m != null) inst.removeModifier(id);
        } else if (m == null || Math.abs(m.amount() - add) > EPS) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, add, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static Far far(Player player, Holder<Attribute> attr, ResourceLocation id, double distSqr) {
        AttributeInstance inst = player.getAttribute(attr);
        AttributeModifier m = inst == null ? null : inst.getModifier(id);
        if (m == null) return Far.NEAR;
        return far(distSqr, inst.getValue() - m.amount(), inst.getValue());
    }

    private static Far blockFar(ServerPlayer player, BlockPos pos) {
        if (ShipFrames.get().isPlot(player.level(), pos)) return Far.NEAR;
        return far(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID, new AABB(pos).distanceToSqr(player.getEyePosition()));
    }

    private static Far entityFar(ServerPlayer player, Entity target) {
        return far(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_ID, target.getBoundingBox().distanceToSqr(player.getEyePosition()));
    }

    /**
     * A block action at {@code pos} ({@code point} the clicked spot, or null): false when Far Use refuses it; an accepted one
     * {@code acts}. A Grab's claim question ({@link Grab#asking}) is no action: it passes untouched.
     */
    private static boolean block(ServerPlayer player, BlockPos pos, @Nullable Vec3 point, boolean acts) {
        if (Grab.asking() || blockFar(player, pos) != Far.FAR) return true;
        if (!WorkSight.seesBlock(player.level(), pos, point, player.getEyePosition(), Grips.body(player))) {
            refuse(player, NO_SIGHT);
            return false;
        }
        if (acts) act(player, point != null ? point : Vec3.atCenterOf(pos), FarUsePacket.Kind.BLOCK);
        return true;
    }

    /** A hit or use on {@code target}: false when Far Use refuses it; an accepted one acts. A Grab's claim question passes. */
    private static boolean entity(ServerPlayer player, Entity target) {
        if (Grab.asking() || entityFar(player, target) != Far.FAR) return true;
        if (target instanceof Player && !TentaworksConfig.value(TentaworksConfig.FAR_USE_PLAYERS)) {
            refuse(player, PLAYERS);
            return false;
        }
        AABB box = target.getBoundingBox();
        if (!WorkSight.seesBox(player.level(), player, box, player.getEyePosition(), Grips.body(player))) {
            refuse(player, NO_SIGHT);
            return false;
        }
        act(player, box.getCenter(), FarUsePacket.Kind.ENTITY);
        return true;
    }

    /** An accepted far action: Air once per tick, a {@link FarUsePacket} to the wearer and trackers at most every {@value #PACKET_GAP} ticks. */
    private static void act(ServerPlayer player, Vec3 target, FarUsePacket.Kind kind) {
        Track t = TRACKS.computeIfAbsent(player.getUUID(), u -> new Track());
        int now = player.server.getTickCount();
        if (due(t.charged, now, CHARGE_GAP)) {
            t.charged = now;
            RigAir.pay(player, Actuators.farUseCost(player));
        }
        if (due(t.sent, now, PACKET_GAP)) {
            t.sent = now;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FarUsePacket(player.getId(), target, kind, TTL));
        }
    }

    private static void refuse(ServerPlayer player, String key) {
        Track t = TRACKS.computeIfAbsent(player.getUUID(), u -> new Track());
        int now = player.server.getTickCount();
        if (key.equals(t.refusal) && !due(t.refused, now, REFUSAL_GAP)) return;
        t.refused = now;
        t.refusal = key;
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD) mine(p, null);
            return;
        }
        if (block(p, e.getPos(), null, true)) {
            mine(p, blockFar(p, e.getPos()) == Far.FAR ? e.getPos().immutable() : null);
            return;
        }
        mine(p, null);
        e.setCanceled(true);
        p.connection.send(new ClientboundBlockUpdatePacket(p.level(), e.getPos()));
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        if (e.getPos().equals(mining(p))) mine(p, null);
        if (!e.isCanceled() && !block(p, e.getPos(), null, false)) e.setCanceled(true);
    }

    private static @Nullable BlockPos mining(ServerPlayer player) {
        Track t = TRACKS.get(player.getUUID());
        return t == null ? null : t.mining;
    }

    private static void mine(ServerPlayer player, @Nullable BlockPos pos) {
        Track t = pos == null ? TRACKS.get(player.getUUID()) : TRACKS.computeIfAbsent(player.getUUID(), u -> new Track());
        if (t != null) t.mining = pos;
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || block(p, e.getPos(), e.getHitVec().getLocation(), true)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || entity(p, e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    private static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || entity(p, e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    private static void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !entity(p, e.getTarget())) e.setCanceled(true);
    }

    /** A sweep reaches as far as the lifted entity range: with {@code farUse.players} off it spares players past arm's length. */
    private static void onDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !e.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || p == victim || TentaworksConfig.value(TentaworksConfig.FAR_USE_PLAYERS)) return;
        if (entityFar(p, victim) != Far.NEAR) e.setCanceled(true);
    }

    private static void onDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof ServerPlayer p) || blockFar(p, e.getPos()) == Far.NEAR) return;
        Vec3 home = p.position();
        for (ItemEntity item : e.getDrops()) {
            item.setPos(home);
            item.setDeltaMovement(Vec3.ZERO);
            item.setNoPickUpDelay();
        }
        int xp = e.getDroppedExperience();
        if (xp <= 0) return;
        e.setDroppedExperience(0);
        ServerLevel level = e.getLevel();
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !level.restoringBlockSnapshots) ExperienceOrb.award(level, home, xp);
    }
}
