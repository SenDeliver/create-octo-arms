package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.network.ActuatorStatePacket;
import dev.tentaworks.network.FarUsePacket;
import dev.tentaworks.network.RigGrabPacket;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.Grab;
import dev.tentaworks.rig.TentacleRigItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the Actuators: what each player's Actuators hold and where a Far Use reaches (from the server), and the
 * wearer's Grab key. The Grab key with an empty main hand asks for a Grab of the mob or Ship block under the crosshair within
 * Reach; again while holding sets it down; attack while holding Throws.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorClient {
    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();

    /**
     * One player's Actuators. {@code heldId} the held mob (-1 none), {@code ship} the held Ship block (null none),
     * {@code farTarget} where the Right Actuator reaches for {@code farTtl} more ticks (null none), {@code refusal} the last
     * refused Grab's lang key (empty none).
     */
    public record State(int heldId, boolean heavy, @Nullable BlockPos ship, Direction shipFace, @Nullable Vec3 farTarget, int farTtl,
                        String refusal) {
        public static final State NONE = new State(-1, false, null, Direction.UP, null, 0, "");

        public boolean holding() {
            return heldId >= 0 || ship != null;
        }

        State held(ActuatorStatePacket p) {
            return new State(p.heldId(), p.heavy(), p.ship(), p.shipFace(), farTarget, farTtl, p.refusal());
        }

        State far(@Nullable Vec3 target, int ttl) {
            return new State(heldId, heavy, ship, shipFace, ttl > 0 ? target : null, Math.max(0, ttl), refusal);
        }

        boolean idle() {
            return !holding() && farTtl <= 0;
        }
    }

    private ActuatorClient() {
    }

    public static State get(Player player) {
        return STATES.getOrDefault(player.getId(), State.NONE);
    }

    public static State local() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? State.NONE : get(player);
    }

    /** The wearer's Rig Speed factor: {@code grab.heavySpeedFactor} while both Actuators hold a heavy target (the server checks the same). */
    public static double speedFactor() {
        return local().heavy() ? Grab.heavyFactor() : 1.0;
    }

    /** The id of the Ship the wearer's Actuators hold (its Grips do not hold the wearer up), or null. */
    public static @Nullable Object heldShip(Level level) {
        BlockPos ship = local().ship();
        Frame frame = ship == null ? null : ShipFrames.get().containing(level, ship);
        return frame == null ? null : frame.id();
    }

    public static void onState(ActuatorStatePacket packet) {
        put(packet.entityId(), STATES.getOrDefault(packet.entityId(), State.NONE).held(packet));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && packet.entityId() == player.getId() && !packet.refusal().isEmpty())
            player.displayClientMessage(Component.translatable(packet.refusal()).withStyle(ChatFormatting.RED), true);
    }

    public static void onFarUse(FarUsePacket packet) {
        put(packet.entityId(), STATES.getOrDefault(packet.entityId(), State.NONE).far(packet.target(), packet.ttl()));
    }

    /** The wearer's own Far Use, predicted (the server sends {@link FarUsePacket} only to the others): reach {@code target} for {@code ttl} ticks. */
    public static void predictFarUse(Vec3 target, int ttl) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) put(player.getId(), get(player).far(target, Math.max(ttl, get(player).farTtl)));
    }

    /**
     * A Throw by the wearer {@code wearerId}: camera kick (own wearer), follow-through, and a watch on the thrown mob for its Impact.
     * The wearer's own attack calls it; observers infer it from a fast release until a server signal calls it for them.
     */
    public static void onThrow(int wearerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(wearerId) instanceof Player wearer)) return;
        ActuatorFx.thrown(wearer, get(wearer).heldId, wearer.getViewVector(1.0F));
    }

    /** An Impact of the Thrown {@code thrownId} at {@code at}, {@code speed} blocks per tick: sparks and a clank, once per Throw. */
    public static void onImpact(int thrownId, Vec3 at, double speed) {
        ActuatorFx.impacted(thrownId);
        ActuatorFx.impact(at, speed);
    }

    private static void put(int id, State s) {
        if (s.idle()) STATES.remove(id);
        else STATES.put(id, s);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!STATES.isEmpty() && !mc.isPaused()) {
            for (Int2ObjectMap.Entry<State> e : STATES.int2ObjectEntrySet())
                if (e.getValue().farTtl > 0) e.setValue(e.getValue().far(e.getValue().farTarget, e.getValue().farTtl - 1));
            STATES.values().removeIf(State::idle);
        }
        while (TentaworksKeys.GRAB.consumeClick()) {
            if (mc.player != null && mc.screen == null) grabKey(mc.player);
        }
    }

    private static void grabKey(LocalPlayer player) {
        if (local().holding()) {
            PacketDistributor.sendToServer(RigGrabPacket.release());
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !Actuators.ready(player)) return;
        HitResult aim = aim(player, TentacleRigItem.reach(player));
        if (aim instanceof EntityHitResult e) PacketDistributor.sendToServer(RigGrabPacket.start(e.getEntity().getId()));
        else if (aim instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && ShipFrames.get().isPlot(player.level(), b.getBlockPos()))
            PacketDistributor.sendToServer(RigGrabPacket.ship(b.getBlockPos(), b.getDirection()));
    }

    /** What the crosshair rests on within {@code reach}: the nearest entity before the first block, else that block. */
    static HitResult aim(LocalPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        BlockHitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double range = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(range));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, sweep, ActuatorClient::grabbable, range * range);
        return entity != null ? entity : block;
    }

    private static boolean grabbable(Entity e) {
        return !e.isSpectator() && e.isPickable();
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !local().holding()) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(RigGrabPacket.throwAlong(mc.player.getViewVector(1.0F)));
        onThrow(mc.player.getId());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        STATES.clear();
    }
}
