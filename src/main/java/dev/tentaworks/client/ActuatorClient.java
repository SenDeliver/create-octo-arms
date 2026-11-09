package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.network.ActuatorFxPacket;
import dev.tentaworks.network.ActuatorStatePacket;
import dev.tentaworks.network.FarUsePacket;
import dev.tentaworks.network.RigGrabPacket;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.rig.ActuatorReach;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.DualWield;
import dev.tentaworks.rig.Grab;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.Hold;
import dev.tentaworks.rig.HoldArm;
import dev.tentaworks.rig.SetDown;
import dev.tentaworks.rig.TentacleRigItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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
 * wearer's Grab key ({@link GrabKeyState}). A press asks for a Grab of the mob or Ship block under the crosshair within Reach
 * of the body, with whichever Actuator is free ({@link GrabRule#arm}); a tap while holding sets the mob down at the looked spot
 * ({@link SetDown}) or releases a Ship; the key held with the wheel moves the hold nearer or farther (the hotbar does not
 * scroll then). Attack while holding Throws when the main hand is empty or the Grab key is down; with an item in the main hand
 * (the off-hand Actuator holds) a plain attack is the item's. The wearer's own Far Use reach comes from the server's
 * {@link FarUsePacket} like everyone else's, so it shows only for actions the server took.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorClient {
    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
    /**
     * Ticks after the wearer's own Throw request (or until the server's answer ends the hold) in which attacks stay with it and
     * the server's THROW is not played again.
     */
    static final int THROW_WAIT = 20;
    private static int throwSent = Integer.MIN_VALUE / 2;
    private static final GrabKeyState KEY = new GrabKeyState();
    private static @Nullable SetDown.Spot spot;
    private static Affordance affordance = Affordance.NONE;

    static {
        DualWield.clientSide(ActuatorClient::holdArm);
    }

    /**
     * One player's Actuators. {@code heldId} the held mob (-1 none), {@code ship} the held Ship block (null none),
     * {@code farTarget} where the {@code farHand} Actuator reaches for {@code farTtl} more ticks (null none), {@code refusal} the
     * last refused Grab's lang key (empty none), {@code arm} which Actuator(s) hold, {@code distance} the hold distance along the
     * look (blocks, 0 when nothing is held).
     */
    public record State(int heldId, boolean heavy, @Nullable BlockPos ship, Direction shipFace, @Nullable Vec3 farTarget, int farTtl,
                        String refusal, HoldArm arm, float distance, InteractionHand farHand) {
        public static final State NONE = new State(-1, false, null, Direction.UP, null, 0, "", HoldArm.NONE, 0, InteractionHand.MAIN_HAND);

        public boolean holding() {
            return heldId >= 0 || ship != null;
        }

        State held(ActuatorStatePacket p) {
            return new State(p.heldId(), p.heavy(), p.ship(), p.shipFace(), farTarget, farTtl, p.refusal(), p.arm(), p.distance(), farHand);
        }

        State far(@Nullable Vec3 target, int ttl, InteractionHand hand) {
            return new State(heldId, heavy, ship, shipFace, ttl > 0 ? target : null, Math.max(0, ttl), refusal, arm, distance, hand);
        }

        boolean idle() {
            return !holding() && farTtl <= 0;
        }
    }

    /** What a Grab key press would do on the crosshair target: whom it fits ({@code arm}) or why not ({@code verdict} for REFUSED). */
    public record Affordance(Kind kind, HoldArm arm, @Nullable GrabRule.Verdict verdict) {
        public static final Affordance NONE = new Affordance(Kind.NONE, HoldArm.NONE, null);

        public enum Kind {
            /** Nothing to Grab under the crosshair, or already holding, or the Actuators cannot act. */
            NONE,
            /** Grabbable with {@code arm}. */
            GRAB,
            /** Heavy or a Ship: both hands must be empty. */
            TWO_HANDS,
            /** Light, but both hands hold items. */
            HANDS_FULL,
            /** The Grab rule says no ({@code verdict}). */
            REFUSED,
            /** Farther than Reach of the body. */
            OUT_OF_REACH
        }
    }

    private ActuatorClient() {
    }

    /** Which Actuator(s) of {@code player} hold (NONE when nothing), as the server last said. */
    public static HoldArm holdArm(Player player) {
        return get(player).arm();
    }

    /** {@code player}'s hold distance along the look in blocks (0 when nothing is held), as the server last said. */
    public static float holdDistance(Player player) {
        return get(player).distance();
    }

    /** The local wearer's set-down spot this tick (feet, box, valid); null when not holding a mob or no block face is under the crosshair. */
    public static @Nullable SetDown.Spot setDownSpot() {
        return spot;
    }

    /** What the Grab key would do on the local crosshair target this tick. */
    public static Affordance affordance() {
        return affordance;
    }

    /** The Grab key is held down now. */
    public static boolean grabKeyDown() {
        return TentaworksKeys.GRAB.isDown();
    }

    /** Dual Wield applies to the block or entity under the local crosshair: the off-hand item would help break or hit it. */
    public static boolean dualWield() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;
        if (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK)
            return DualWield.on(p) && DualWield.effective(p.getOffhandItem().getDestroySpeed(mc.level.getBlockState(b.getBlockPos())));
        return mc.hitResult instanceof EntityHitResult e && DualWield.helps(p, e.getEntity());
    }

    /** {@code point} is within {@code player}'s Actuator Reach of the body (the one rule client and server use). */
    public static boolean reaches(Player player, Vec3 point) {
        return ActuatorReach.reaches(player, point);
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
        if (player != null && packet.entityId() == player.getId() && !get(player).holding()) throwSent = Integer.MIN_VALUE / 2;
        if (player != null && packet.entityId() == player.getId() && !packet.refusal().isEmpty())
            player.displayClientMessage(Component.translatable(packet.refusal()).withStyle(ChatFormatting.RED), true);
    }

    public static void onFarUse(FarUsePacket packet) {
        put(packet.entityId(), STATES.getOrDefault(packet.entityId(), State.NONE).far(packet.target(), packet.ttl(), packet.hand()));
    }

    /**
     * The server's Throw and Impact signals. The wearer's own Throw already played when the attack sent it, so its THROW (sent
     * just before the state that ends the hold) comes back silent.
     */
    public static void onFx(ActuatorFxPacket packet) {
        switch (packet.kind()) {
            case THROW -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null || packet.entityId() != player.getId() || !throwWaiting(player)) onThrow(packet.entityId(), packet.at());
            }
            case IMPACT -> onImpact(packet.entityId(), packet.at(), packet.speed());
        }
    }

    /** A Throw by the wearer {@code wearerId} along {@code dir}: camera kick (own wearer) and follow-through. */
    public static void onThrow(int wearerId, Vec3 dir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(wearerId) instanceof Player wearer) ActuatorFx.thrown(wearer, dir);
    }

    /** An Impact of entity {@code thrown} at {@code at}, {@code speed} blocks per tick: sparks and a sound that fits it. */
    public static void onImpact(int thrown, Vec3 at, double speed) {
        ActuatorFx.impact(thrown, at, speed);
    }

    private static boolean throwWaiting(LocalPlayer player) {
        int age = player.tickCount - throwSent;
        return age >= 0 && age <= THROW_WAIT;
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
                if (e.getValue().farTtl > 0) e.setValue(e.getValue().far(e.getValue().farTarget, e.getValue().farTtl - 1, e.getValue().farHand));
            STATES.values().removeIf(State::idle);
        }
        int clicks = 0;
        while (TentaworksKeys.GRAB.consumeClick()) clicks++;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            KEY.reset();
            spot = null;
            affordance = Affordance.NONE;
            return;
        }
        State s = get(player);
        spot = s.heldId() >= 0 && mc.level.getEntity(s.heldId()) instanceof Entity held
                ? SetDown.spot(mc.level, player, held, player.getEyePosition(), player.getViewVector(1.0F), TentacleRigItem.reach(player), 0) : null;
        affordance = s.holding() ? Affordance.NONE : affordance(player);
        if (mc.screen != null) {
            KEY.reset();
            return;
        }
        switch (KEY.tick(clicks, TentaworksKeys.GRAB.isDown(), s.holding())) {
            case GRAB -> grab(player);
            case SET_DOWN -> setDown(s);
            case NONE -> {
            }
        }
    }

    private static void grab(LocalPlayer player) {
        if (!Actuators.ready(player)) return;
        HitResult aim = target(player);
        if (aim instanceof EntityHitResult e) PacketDistributor.sendToServer(RigGrabPacket.start(e.getEntity().getId()));
        else if (aim instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && ShipFrames.get().isPlot(player.level(), b.getBlockPos()))
            PacketDistributor.sendToServer(RigGrabPacket.ship(b.getBlockPos(), b.getDirection()));
    }

    /** A tap while holding: a mob to the looked spot (the server checks it again), else where it hangs; a Ship is let go. */
    private static void setDown(State s) {
        if (s.ship() == null && spot != null) PacketDistributor.sendToServer(RigGrabPacket.place(spot.feet()));
        else PacketDistributor.sendToServer(RigGrabPacket.release());
    }

    /** What the Grab key reaches: the crosshair's mob or block as far as the Actuators' lifted pick ({@link ActuatorReach#pickRange}). */
    static HitResult target(LocalPlayer player) {
        return aim(player, ActuatorReach.pickRange(TentacleRigItem.reach(player)));
    }

    private static Affordance affordance(LocalPlayer player) {
        if (!Actuators.ready(player)) return Affordance.NONE;
        double reach = TentacleRigItem.reach(player);
        boolean main = player.getMainHandItem().isEmpty(), off = player.getOffhandItem().isEmpty();
        HitResult aim = target(player);
        if (aim instanceof EntityHitResult e) {
            GrabRule.Verdict v = GrabRule.check(Grab.facts(player, e.getEntity(), heldByAnyone(e.getEntity())),
                    new GrabRule.Rules(reach, TentaworksConfig.value(TentaworksConfig.GRAB_TAMED_OTHERS)));
            if (v == GrabRule.Verdict.GONE) return Affordance.NONE;
            if (v == GrabRule.Verdict.TOO_FAR) return new Affordance(Affordance.Kind.OUT_OF_REACH, HoldArm.NONE, v);
            if (!v.ok()) return new Affordance(Affordance.Kind.REFUSED, HoldArm.NONE, v);
            return arms(v.actuators(), main, off);
        }
        if (aim instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && ShipFrames.get().isPlot(player.level(), b.getBlockPos())) {
            Frame frame = ShipFrames.get().containing(player.level(), b.getBlockPos());
            if (frame == null || !TentaworksConfig.value(TentaworksConfig.GRAB_SHIPS) || !ShipFrames.enabled()) return Affordance.NONE;
            if (!ActuatorReach.within(Grips.body(player), frame.toWorld(b.getLocation()), reach + GrabRule.SLACK))
                return new Affordance(Affordance.Kind.OUT_OF_REACH, HoldArm.NONE, GrabRule.Verdict.TOO_FAR);
            return arms(2, main, off);
        }
        return Affordance.NONE;
    }

    private static Affordance arms(int actuators, boolean mainEmpty, boolean offEmpty) {
        HoldArm arm = GrabRule.arm(actuators, mainEmpty, offEmpty);
        if (arm.holding()) return new Affordance(Affordance.Kind.GRAB, arm, null);
        GrabRule.Verdict v = GrabRule.hands(actuators);
        return new Affordance(v == GrabRule.Verdict.TWO_HANDS ? Affordance.Kind.TWO_HANDS : Affordance.Kind.HANDS_FULL, HoldArm.NONE, v);
    }

    private static boolean heldByAnyone(Entity e) {
        for (State s : STATES.values()) if (s.heldId() == e.getId()) return true;
        return false;
    }

    /**
     * The wheel with the Grab key held while holding moves the hold ({@link Hold#NOTCH} a notch, up: farther) and not the hotbar.
     */
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !TentaworksKeys.GRAB.isDown() || !local().holding()) return;
        event.setCanceled(true);
        double dy = event.getScrollDeltaY();
        int n = (int) Math.round(dy);
        if (n == 0) n = (int) Math.signum(dy);
        if (n == 0) return;
        KEY.scrolled();
        PacketDistributor.sendToServer(RigGrabPacket.distance(n));
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

    /**
     * Attack while holding Throws, never hits, when the main hand is empty or the Grab key is down: one THROW per hold
     * (holding attack fires this every tick until the server's answer lands), the kick and follow-through at once, no vanilla
     * swing. With an item in the main hand a plain attack is the item's own.
     */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !local().holding()) return;
        if (!mc.player.getMainHandItem().isEmpty() && !TentaworksKeys.GRAB.isDown()) return;
        KEY.threw();
        event.setCanceled(true);
        event.setSwingHand(false);
        if (throwWaiting(mc.player)) return;
        Vec3 look = mc.player.getViewVector(1.0F);
        throwSent = mc.player.tickCount;
        PacketDistributor.sendToServer(RigGrabPacket.throwAlong(look));
        onThrow(mc.player.getId(), look);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        KEY.reset();
        spot = null;
        affordance = Affordance.NONE;
        throwSent = Integer.MIN_VALUE / 2;
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        STATES.clear();
        throwSent = Integer.MIN_VALUE / 2;
    }
}
