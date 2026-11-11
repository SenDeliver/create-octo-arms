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
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
 * Far Use, server side (adr/0007): each hand's Actuator acts for that hand. While they may act ({@link #lifts}), transient modifiers lift the
 * wearer's block and entity interaction ranges to {@link ActuatorReach#pickRange}, so vanilla's own click paths reach every point within
 * Reach of the body; vanilla syncs both attributes to every client, so the wearer's crosshair matches, and the crosshair keeps only
 * picks within Reach of the body ({@link ActuatorReach#accepts}); the gates refuse the rest ({@link Far#OUT}). A target past arm's
 * length ({@link #beyond}, the range without the lift) must also be in sight ({@link WorkSight}) and may be a player only with
 * {@code farUse.players}: the gates on the interaction events refuse the rest. Only an action that did something costs Air
 * and is told to the clients ({@link FarUsePacket}): a use that consumed the click ({@link #usedOn}), a hit that hurt
 * ({@link #hit}), a mining start that makes progress ({@link #mines}); a use that passes (an empty hand on stone) is nothing.
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
    /** Ticks a {@link FarUsePacket} keeps the acting hand's Actuator at its target. */
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
        /** Past Reach of the body (and past vanilla's own range): refused. */
        OUT
    }

    /**
     * Server ticks of the wearer's last charge, packet and refusal (one click fires several events: both hands, interact-at and
     * interact) and the block being mined past arm's length (null none).
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

    /** The Actuators lend the hands their Reach: fitted, allowed by the Rig Setting now, no Extendo Grip in hand, no heavy hold, not limp. */
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

    /**
     * Where a target lies ({@link ActuatorReach}): {@code eyeDistSqr} from the eye and {@code bodyDistSqr} from the body to its box
     * (squared), {@code base} the range without the lift, {@code lifted} whether the Actuator lends its Reach now. Past arm's length
     * it is a Far Use when its box comes within Reach + {@value #SLACK} of the body (or within vanilla's own {@value #SLACK} of
     * slack past {@code base}), else out of reach: the lifted range no longer bounds anything, so the gates refuse it.
     */
    public static Far far(double eyeDistSqr, double base, boolean lifted, double bodyDistSqr, double reach) {
        if (!lifted || !beyond(eyeDistSqr, base)) return Far.NEAR;
        double r = reach + SLACK, v = base + SLACK;
        return bodyDistSqr <= r * r || eyeDistSqr <= v * v ? Far.FAR : Far.OUT;
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
        double reach = on ? ActuatorReach.pickRange(TentacleRigItem.reach(player)) : 0;
        lift(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), REACH_ID, reach);
        lift(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), ENTITY_ID, reach);
        keepMining(player, on);
    }

    /** While a far block is mined the main-hand Actuator stays at it: the {@link FarUsePacket} is told again before its ttl runs out. */
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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FarUsePacket(player.getId(), Vec3.atCenterOf(t.mining), FarUsePacket.Kind.BLOCK, TTL,
                InteractionHand.MAIN_HAND));
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

    /** {@link #far(double, double, boolean, double, double)} for a target box under this player's range {@code attr} (lifted by modifier {@code id}). */
    public static Far far(Player player, Holder<Attribute> attr, ResourceLocation id, AABB box) {
        AttributeInstance inst = player.getAttribute(attr);
        AttributeModifier m = inst == null ? null : inst.getModifier(id);
        if (m == null) return Far.NEAR;
        return far(box.distanceToSqr(player.getEyePosition()), inst.getValue() - m.amount(), m.amount() > EPS, box.distanceToSqr(Grips.body(player)),
                TentacleRigItem.reach(player));
    }

    /**
     * The crosshair pick of a wearer from {@code eye} (this frame's), cut to what the hands and the Actuators reach: with a lifted
     * range, a hit past the unlifted one counts only within Reach of the body ({@link ActuatorReach#accepts}); else it becomes a
     * miss, as vanilla's own range filter makes it. Ship blocks are left to the Ship mod.
     */
    public static HitResult filter(Player player, HitResult hit, Vec3 eye) {
        if (hit.getType() == HitResult.Type.MISS) return hit;
        boolean entity = hit instanceof EntityHitResult;
        if (!entity && hit instanceof BlockHitResult b && ShipFrames.get().isPlot(player.level(), b.getBlockPos())) return hit;
        AttributeInstance inst = player.getAttribute(entity ? Attributes.ENTITY_INTERACTION_RANGE : Attributes.BLOCK_INTERACTION_RANGE);
        AttributeModifier m = inst == null ? null : inst.getModifier(entity ? ENTITY_ID : REACH_ID);
        if (m == null || m.amount() <= EPS) return hit;
        Vec3 at = hit.getLocation();
        if (ActuatorReach.accepts(eye, ActuatorReach.bodyFromEye(player, eye), at, inst.getValue() - m.amount(), TentacleRigItem.reach(player))) return hit;
        return BlockHitResult.miss(at, Direction.getNearest(at.x - eye.x, at.y - eye.y, at.z - eye.z), BlockPos.containing(at));
    }

    public static Far blockFar(Player player, BlockPos pos) {
        if (ShipFrames.get().isPlot(player.level(), pos)) return Far.NEAR;
        return far(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID, new AABB(pos));
    }

    public static Far entityFar(Player player, Entity target) {
        return far(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_ID, target.getBoundingBox());
    }

    /**
     * A block action at {@code pos} ({@code face} the clicked face and {@code point} the clicked spot, each null when unknown):
     * false when Far Use refuses it. A Grab's claim question ({@link Grab#asking}) is no action: it passes untouched.
     */
    private static boolean block(ServerPlayer player, BlockPos pos, @Nullable Direction face, @Nullable Vec3 point) {
        if (Grab.asking()) return true;
        Far far = blockFar(player, pos);
        if (far != Far.FAR) return far == Far.NEAR;
        if (!WorkSight.seesBlock(player, pos, face, point)) {
            refuse(player, NO_SIGHT);
            return false;
        }
        return true;
    }

    /** A hit or use on {@code target}: false when Far Use refuses it. A Grab's claim question passes. */
    private static boolean entity(ServerPlayer player, Entity target) {
        if (Grab.asking()) return true;
        Far far = entityFar(player, target);
        if (far != Far.FAR) return far == Far.NEAR;
        if (target instanceof Player && !TentaworksConfig.value(TentaworksConfig.FAR_USE_PLAYERS)) {
            refuse(player, PLAYERS);
            return false;
        }
        if (!WorkSight.seesEntity(player, target)) {
            refuse(player, NO_SIGHT);
            return false;
        }
        return true;
    }

    /** After a use on a block with {@code hand}: a far one that consumed the click (the block's use, the item's use on it, a placement) acts. */
    public static void usedOn(ServerPlayer player, BlockHitResult hit, InteractionHand hand, InteractionResult result) {
        if (result.consumesAction() && !Grab.asking() && blockFar(player, hit.getBlockPos()) == Far.FAR) act(player, hit.getLocation(), FarUsePacket.Kind.BLOCK, hand);
    }

    /** After a use on an entity with {@code hand}: a far one that consumed the click acts. */
    public static void usedOn(ServerPlayer player, Entity target, InteractionHand hand, InteractionResult result) {
        if (result.consumesAction() && !Grab.asking() && entityFar(player, target) == Far.FAR)
            act(player, target.getBoundingBox().getCenter(), FarUsePacket.Kind.ENTITY, hand);
    }

    /** After a hit that hurt its main target: a far one acts (the main hand's). */
    public static void hit(ServerPlayer player, Entity target) {
        if (entityFar(player, target) == Far.FAR) act(player, target.getBoundingBox().getCenter(), FarUsePacket.Kind.ENTITY, InteractionHand.MAIN_HAND);
    }

    /** A mining start at {@code pos} that vanilla will carry out and that makes progress (instant in creative): the same checks, in its order. */
    static boolean mines(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (player.isCreative()) return true;
        return !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer()) && state.getDestroyProgress(player, level, pos) > 0;
    }

    /** An accepted far action: Air once per tick, a {@link FarUsePacket} to the wearer and trackers at most every {@value #PACKET_GAP} ticks. */
    private static void act(ServerPlayer player, Vec3 target, FarUsePacket.Kind kind, InteractionHand hand) {
        Track t = TRACKS.computeIfAbsent(player.getUUID(), u -> new Track());
        int now = player.server.getTickCount();
        if (due(t.charged, now, CHARGE_GAP)) {
            t.charged = now;
            RigAir.pay(player, Actuators.farUseCost(player));
        }
        if (due(t.sent, now, PACKET_GAP)) {
            t.sent = now;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FarUsePacket(player.getId(), target, kind, TTL, hand));
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
        if (block(p, e.getPos(), e.getFace(), null)) {
            BlockPos far = blockFar(p, e.getPos()) == Far.FAR ? e.getPos().immutable() : null;
            boolean mines = far != null && mines(p, far);
            if (mines) act(p, Vec3.atCenterOf(far), FarUsePacket.Kind.BLOCK, InteractionHand.MAIN_HAND);
            mine(p, mines ? far : null);
            return;
        }
        mine(p, null);
        e.setCanceled(true);
        p.connection.send(new ClientboundBlockUpdatePacket(p.level(), e.getPos()));
    }

    /**
     * A break ends the far mining of its block. It needs no gate of its own: vanilla breaks a block only after a mining start on
     * it, which the gate passed (an instant one at once, the rest on the stop or a tick later), while the look may have moved on.
     */
    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && e.getPos().equals(mining(p))) mine(p, null);
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
        if (!(e.getEntity() instanceof ServerPlayer p) || block(p, e.getPos(), e.getHitVec().getDirection(), e.getHitVec().getLocation())) return;
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
