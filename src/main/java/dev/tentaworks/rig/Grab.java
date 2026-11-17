package dev.tentaworks.rig;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipBody;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.mixin.CreeperAccessor;
import dev.tentaworks.network.ActuatorFxPacket;
import dev.tentaworks.network.ActuatorStatePacket;
import dev.tentaworks.network.RigGrabPacket;
import dev.tentaworks.registry.TentaworksAttachments;
import dev.tentaworks.registry.TentaworksLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Grab and Throw, server side (adr/0007: the server is the authority, the client only asks with
 * {@link RigGrabPacket} and draws what {@link ActuatorStatePacket} tells it). START takes a mob under the wearer's crosshair:
 * fitted Actuators with Air, at most one start per {@value #START_EVERY} ticks, the target rules ({@link GrabRule}), a free
 * Actuator for it ({@link GrabRule#arm}: a light mob the off-hand one, else the main-hand one, each needing its hand empty; a heavy
 * mob or a Ship both, with both hands empty) and then the loader's entity-interaction event, so claim and protection mods can
 * refuse. The mob is then Pinned ({@link #pinned}) and swept to the hold point ({@link Hold}) on the holding Actuator's side every
 * tick, at the hold distance the wheel sets (DISTANCE); the entity tracker is told each position sent ({@link #sent}); a heavy
 * one takes both Actuators and slows the wearer to {@code grab.heavySpeedFactor}. Holding pays {@code air.grab}, a Throw {@code air.throw}. PLACE carries a held mob to
 * the looked spot ({@link SetDown}, checked here again) and sets it down there.
 * Ship Grab (Sable): SHIP takes the Ship owning a plot block with both Actuators; its body velocity drives the held face to
 * where it was in the wearer's view at the start (no farther than Reach − {@value #SHIP_INSIDE} from the eye), so it swings
 * round the wearer as they look about, and turns its yaw with the wearer's ({@link Hold#shipPoint}, {@link Hold#shipTurn};
 * {@value #SHIP_GAIN}/s of the error plus the target's own move, which also cancels gravity). Sable collides it on the way.
 * The wheel one notch past the farthest spot holds it Overhead ({@link Hold#shipWheel}, {@link Hold#shipOverhead}: its lowest
 * point above the eye, on the level of the wearer's yaw); TURN (the Grab key and use held, the mouse) turns it in the wearer's view
 * ({@link Hold#shipTurnInput}, at most {@value #TURNS_PER_TICK} packets a tick, banked and turned at most {@value Hold#TURN_RATE}° a
 * tick), and the turn stays when it is let go. The two tips hold their own points on the hull and regrip hand over hand
 * ({@link ShipHands}); the hold and the Turn pivot on their midpoint.
 * The held Ship's build ({@link ShipBuild}: it and the Ships touching it, found again every tick) counts as one: a build that is
 * all that holds the wearer up is refused ({@link GrabRule#holdsUp}): one Grip off it is enough, the wearer's Grips on it let go
 * at the Grab and whenever the build grows ({@link RigServer#letGo}) and none hold on to it or hold the wearer up while it is
 * held. A held build the wearer comes to stand on is let go (underfoot).
 * Every hold ends in {@link #release}: set down where it is (calm for {@value #CALM_TICKS} ticks, onto a seat, boat or minecart
 * when one is under the crosshair or the target) or thrown (awake at once, its Impact armed, {@link ActuatorFxPacket} THROW to
 * the clients). The automatic ends are
 * {@link Hold#ends}, plus logout and the target or wearer changing dimension; Stow, Setting Down, Rides, glides and Falling
 * keep the hold.
 */
public final class Grab {
    public static final int START_EVERY = 5;
    public static final int CALM_TICKS = 20;
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, TentaworksIds.id(TentaworksIds.BOSSES));
    static final double SHIP_GAIN = 10.0;
    static final double SHIP_MAX_SPEED = 30.0;
    public static final double SHIP_INSIDE = 0.5;
    static final int TURNS_PER_TICK = 2;
    private static final ResourceLocation HEAVY_ID = TentaworksIds.id(TentaworksIds.ACTUATOR_HEAVY);
    private static final Map<UUID, Held> HOLDS = new HashMap<>();
    private static final Map<UUID, Integer> STARTED = new HashMap<>();
    private static final Map<Entity, Vec3> SENT = new WeakHashMap<>();
    private static boolean asking;
    private static Consumer<Event> listener = e -> {
    };

    /** One start, refusal, release or throw (dev trace): {@code why} is the refusal or release reason, {@code speed} the throw's. */
    public record Event(ServerPlayer wearer, String kind, @Nullable Entity target, String why, int actuators, double speed, double mass) {
    }

    /** The clients are told the hold distance again once it has drifted this far (the default one changes as the wearer turns), blocks. */
    private static final double DISTANCE_TOLD = 0.05;

    private static final class Held {
        final Entity target;
        final boolean heavy;
        final HoldArm arm;
        double distance = Double.NaN;
        float told;
        @Nullable Vec3 placeFrom;
        @Nullable Vec3 placeTo;
        int placeTicks;
        @Nullable Vec3 seatEye;
        @Nullable Vec3 seatLook;
        int far;
        Vec3 centre;
        ShipBody ship;
        Level level;
        BlockPos block;
        Direction face;
        Vec3 local;
        Vec3 rel;
        float yaw;
        Quaterniond orientation;
        Quaterniond user = new Quaterniond();
        boolean overhead;
        Direction tangent = Direction.NORTH;
        int turnTick, turns;
        Vec3 lastAim;
        @Nullable Quaterniond lastWant;
        double mass = -1;
        Vec3 taken;
        Vec3 grabLocal;
        ShipHands hands;
        Vec3 correction = Vec3.ZERO;
        double[] bank = {0, 0};
        List<ShipBody> parts = List.of();
        Set<Object> build = Set.of();

        Held(@Nullable Entity target, boolean heavy, HoldArm arm) {
            this.target = target;
            this.heavy = heavy;
            this.arm = arm;
        }
    }

    private Grab() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) logout(p);
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) release(p, "death", null);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) release(p, "dimension", null);
        });
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent e) -> {
            ServerPlayer holder = holder(e.getEntity());
            if (holder != null) release(holder, "dimension", null);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getTarget() instanceof ServerPlayer wearer && e.getEntity() instanceof ServerPlayer watcher && holding(wearer))
                PacketDistributor.sendToPlayer(watcher, state(wearer, ""));
        });
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getTarget() == target(p) && p.getMainHandItem().isEmpty()) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) release(p, "stop", null);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            HOLDS.clear();
            STARTED.clear();
            SENT.clear();
        });
        Impact.init();
        RigEvents.init();
    }

    public static void listen(Consumer<Event> l) {
        listener = l;
    }

    public static void onPacket(ServerPlayer wearer, RigGrabPacket packet) {
        switch (packet.kind()) {
            case START -> start(wearer, packet.entityId());
            case SHIP -> startShip(wearer, packet.block(), packet.face());
            case RELEASE -> {
                if (!release(wearer, "release", null)) reply(wearer, "");
            }
            case THROW -> throwAlong(wearer, packet.look());
            case PLACE -> place(wearer, packet.look());
            case DISTANCE -> distance(wearer, packet.notches());
            case TURN -> turn(wearer, packet.look().x, packet.look().y);
        }
    }

    public static boolean holding(ServerPlayer wearer) {
        return HOLDS.containsKey(wearer.getUUID());
    }

    /** Which Actuator(s) hold, NONE when nothing is held. */
    public static HoldArm arm(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null ? HoldArm.NONE : h.arm;
    }

    /** The hold distance along the look this tick (blocks), 0 when nothing is held (dev trace, state packet). */
    public static double distance(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null) return 0;
        if (h.ship != null) return h.rel == null ? 0 : h.rel.x;
        return Double.isNaN(h.distance) ? Hold.defaultDistance(h.target.getBbWidth(), wearer.getYRot()) : h.distance;
    }

    /** The held mob is being carried to its set-down spot. */
    public static boolean placing(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h != null && h.placeTo != null;
    }

    /** The held target takes both Actuators (a heavy mob or a Ship). */
    public static boolean heavy(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h != null && h.heavy;
    }

    /** The held mob, or null (nothing, or a Ship). */
    public static @Nullable Entity target(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null ? null : h.target;
    }

    /** The plot block the Actuators hold a Ship by, or null. */
    public static @Nullable BlockPos ship(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? null : h.block;
    }

    /** The held Ship is held Overhead. */
    public static boolean overhead(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h != null && h.ship != null && h.overhead;
    }

    /** The wearer's Turn of the held Ship (identity when none or not Turned), a copy (dev trace). */
    public static Quaterniond shipUser(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? new Quaterniond() : new Quaterniond(h.user);
    }

    /** The lowest world point of the held Ship now, NaN when none or unknown (dev trace). */
    public static double shipBottom(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        AABB b = h == null || h.ship == null || !h.ship.alive() ? null : h.ship.worldBounds();
        return b == null ? Double.NaN : b.minY;
    }

    /** The in-face direction of the held Ship's face the tips grip along, null when no Ship is held (dev trace). */
    public static @Nullable Direction shipTangent(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? null : h.tangent;
    }

    /** The held Ship's tips (dev trace), or null. */
    public static @Nullable ShipHands shipHands(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null ? null : h.hands;
    }

    /** Tip {@code i} of the held Ship in the world now, or null (dev trace). */
    public static @Nullable Vec3 shipTip(ServerPlayer wearer, int i) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.hands == null || !h.ship.alive() ? null : h.ship.frame().toWorld(h.hands.tip(i).at());
    }

    /** Tip {@code i} of the held Ship as the clients are told it, or null (dev trace). */
    public static ActuatorStatePacket.@Nullable Tip shipTipTold(ServerPlayer wearer, int i) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.hands == null ? null : tip(h, i);
    }

    /** The Turn banked and not yet turned (yaw, pitch degrees), the hold's correction (blocks) and the Ship body's aim, or null (dev trace). */
    public static double @Nullable [] shipTurnBank(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? null : new double[] {h.bank[0], h.bank[1], h.correction.length()};
    }

    /** Where the hold drives the held Ship's body this tick, or null (dev trace). */
    public static @Nullable Vec3 shipAim(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? null : h.lastAim;
    }

    /** Sable mass of the held Ship, -1 when none (dev trace). */
    public static double shipMass(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? -1 : h.mass;
    }

    /** This tick's hold point (box centre for a mob, the held face for a Ship), or null (dev trace). */
    public static @Nullable Vec3 centre(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null ? null : h.centre;
    }

    /**
     * Where Grab last put {@code e} for the clients, once ({@code ServerEntitySentMixin}): the entity tracker takes it as the base
     * of its next move, as the clients did, so a move after a hold (Set Down, Throw) lands where the server has it.
     */
    public static @Nullable Vec3 sent(Entity e) {
        return SENT.isEmpty() ? null : SENT.remove(e);
    }

    /**
     * True only while a Grab asks the entity-interaction or right-click-block event whether claim mods allow it: not a real use, so a
     * listener that gates reach or sight (Far Use) lets it pass.
     */
    public static boolean asking() {
        return asking;
    }

    public static double heavyFactor() {
        return TentaworksConfig.value(TentaworksConfig.GRAB_HEAVY_SPEED_FACTOR);
    }

    public static void logout(ServerPlayer wearer) {
        release(wearer, "logout", null);
        STARTED.remove(wearer.getUUID());
    }

    /** A Grip on the build of the Ship this wearer holds ({@link ShipBuild}): it does not carry the wearer. */
    public static boolean onHeldShip(ServerPlayer wearer, Grip grip) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null || h.ship == null || !ShipFrames.get().isPlot(wearer.level(), grip.block())) return false;
        Frame f = ShipFrames.get().containing(wearer.level(), grip.block());
        return f != null && h.build.contains(f.id());
    }

    /** How many Ships the held Ship's build has, 0 when no Ship is held (dev trace). */
    public static int shipBuild(ServerPlayer wearer) {
        Held h = HOLDS.get(wearer.getUUID());
        return h == null || h.ship == null ? 0 : h.build.size();
    }

    /**
     * Every AI tick of a mob ({@code MobPinnedMixin}): true when it is Pinned (held by a hold that still exists, or set down and
     * still calm); it is then kept quiet: no target, no navigation, no walking or jumping, a Creeper's fuse back to zero.
     * A stale or spent Pinned is removed.
     */
    public static boolean pinned(Mob mob) {
        Pinned p = mob.getExistingDataOrNull(TentaworksAttachments.PINNED);
        if (p == null || mob.level().isClientSide()) return false;
        Held h = p.held() ? HOLDS.get(p.holder()) : null;
        if (p.held() ? h == null || h.target != mob : !p.calm(mob.level().getGameTime())) {
            mob.removeData(TentaworksAttachments.PINNED);
            return false;
        }
        if (mob.getTarget() != null) mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setXxa(0);
        mob.setYya(0);
        mob.setZza(0);
        mob.setJumping(false);
        if (mob instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
            ((CreeperAccessor) creeper).tentaworks$setSwell(0);
        }
        return true;
    }

    private static @Nullable ServerPlayer holder(Entity target) {
        if (target.level().isClientSide() || target.getServer() == null) return null;
        for (Map.Entry<UUID, Held> e : HOLDS.entrySet())
            if (e.getValue().target == target) return target.getServer().getPlayerList().getPlayer(e.getKey());
        return null;
    }

    private static boolean heldByAnyone(Entity target) {
        for (Held h : HOLDS.values()) if (h.target == target) return true;
        return false;
    }

    private static boolean shipHeldByAnyone(Object id) {
        for (Held h : HOLDS.values()) if (h.ship != null && h.ship.id().equals(id)) return true;
        return false;
    }

    /** At most one start per {@value #START_EVERY} ticks. */
    private static boolean rated(ServerPlayer wearer) {
        int now = wearer.server.getTickCount();
        Integer last = STARTED.get(wearer.getUUID());
        if (last != null && now - last >= 0 && now - last < START_EVERY) return false;
        STARTED.put(wearer.getUUID(), now);
        return true;
    }

    /** Why this wearer cannot Grab now ("" silently), or null. */
    private static @Nullable String wearerRefusal(ServerPlayer wearer) {
        if (holding(wearer) || wearer.isSpectator() || !wearer.isAlive() || !Actuators.fitted(wearer) || !rated(wearer)) return "";
        return RigAir.has(wearer) ? null : "no_air";
    }

    /** The free Actuator(s) for a target needing {@code actuators}, or NONE. */
    private static HoldArm arm(ServerPlayer wearer, int actuators) {
        return GrabRule.arm(actuators, wearer.getMainHandItem().isEmpty(), wearer.getOffhandItem().isEmpty());
    }

    private static String key(GrabRule.Verdict v) {
        return v.name().toLowerCase(Locale.ROOT);
    }

    /** The lang key of the action-bar line for a refusing verdict. */
    public static String refusalKey(GrabRule.Verdict v) {
        return TentaworksLang.refusalKey(TentaworksLang.GRAB, key(v));
    }

    private static void start(ServerPlayer wearer, int id) {
        Entity target = wearer.level().getEntity(id);
        String why = wearerRefusal(wearer);
        GrabRule.Verdict verdict = null;
        HoldArm arm = HoldArm.NONE;
        if (why == null) {
            verdict = GrabRule.check(facts(wearer, target), rules(wearer));
            if (!verdict.ok()) why = verdict == GrabRule.Verdict.GONE ? "" : key(verdict);
        }
        if (why == null) {
            arm = arm(wearer, verdict.actuators());
            if (arm == HoldArm.NONE) why = key(GrabRule.hands(verdict.actuators()));
        }
        if (why == null && claimed(wearer, target, arm == HoldArm.OFF ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)) why = "claimed";
        if (why != null) {
            refuse(wearer, target, why, -1);
            return;
        }
        Held h = new Held(target, verdict == GrabRule.Verdict.HEAVY, arm);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
        target.removeData(TentaworksAttachments.THROWN);
        if (target instanceof Mob mob) {
            mob.setData(TentaworksAttachments.PINNED, new Pinned(wearer.getUUID(), 0));
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        HOLDS.put(wearer.getUUID(), h);
        if (h.heavy) heavySpeed(wearer, true);
        listener.accept(new Event(wearer, "start", target, key(verdict), verdict.actuators(), 0, -1));
        changed(wearer);
        if (target instanceof Blaze) RigEvents.fire(wearer, RigEvents.GRAB_BLAZE, 1);
    }

    /** The loader's entity-interaction event, as a use of the holding hand (empty) on the target: cancelled means a claim or protection mod said no. */
    private static boolean claimed(ServerPlayer wearer, Entity target, InteractionHand hand) {
        return GrabRule.claimed(true, () -> ask(() -> CommonHooks.onInteractEntity(wearer, target, hand) != null));
    }

    /**
     * As an empty-handed right click on that Ship block, asked twice: in its plot (where Sable keeps every Ship block, as a real
     * click on it is seen) and where it is in the world (a Ship parked inside a claim). Either no refuses. The click lands at the
     * block centre, never on a face, so Create's value boxes and filter slots ignore it instead of acting on it.
     */
    private static boolean claimed(ServerPlayer wearer, BlockPos block, Direction face, Vec3 world) {
        return claimedAt(wearer, block, face) || claimedAt(wearer, BlockPos.containing(world), face);
    }

    private static boolean claimedAt(ServerPlayer wearer, BlockPos pos, Direction face) {
        return GrabRule.claimed(wearer.level().mayInteract(wearer, pos), () -> ask(() -> CommonHooks.onRightClickBlock(wearer,
                InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, true)).isCanceled()));
    }

    private static boolean ask(BooleanSupplier post) {
        asking = true;
        try {
            return post.getAsBoolean();
        } finally {
            asking = false;
        }
    }

    private static GrabRule.Target facts(ServerPlayer wearer, @Nullable Entity target) {
        return facts(wearer, target, target != null && heldByAnyone(target));
    }

    /** What {@code wearer}'s side knows of {@code target} for {@link GrabRule#check}; {@code held}: some wearer holds it. */
    public static GrabRule.Target facts(Player wearer, @Nullable Entity target, boolean held) {
        if (target == null || target.isRemoved() || !target.isAlive() || target.isSpectator())
            return new GrabRule.Target(false, true, false, false, false, false, false, 0, 0, 0, false, Double.MAX_VALUE, false);
        boolean boss = target.getType().is(BOSSES) || target instanceof EnderDragon || target instanceof WitherBoss;
        return new GrabRule.Target(target instanceof LivingEntity, false, target instanceof Player, target.isPassenger() || target.isVehicle(),
                held, boss, GrabDeny.denied(EntityType.getKey(target.getType())), target instanceof LivingEntity l ? l.getMaxHealth() : 0, target.getBbWidth(), target.getBbHeight(),
                othersAnimal(wearer, target), Math.sqrt(target.getBoundingBox().distanceToSqr(Grips.body(wearer))), sight(wearer, target));
    }

    /** The Server Config's rules for {@code wearer}'s Grab, the same on the server and in the client's prediction. */
    public static GrabRule.Rules rules(Player wearer) {
        return new GrabRule.Rules(TentacleRigItem.reach(wearer), TentaworksConfig.value(TentaworksConfig.GRAB_TAMED_OTHERS),
                2 * TentaworksConfig.value(TentaworksConfig.GRAB_MAX_HEARTS));
    }

    /** Tamed or owned by another player, or leashed to anything but the wearer (a fence knot belongs to someone too). */
    private static boolean othersAnimal(Player wearer, Entity target) {
        if (target instanceof OwnableEntity own && own.getOwnerUUID() != null && !own.getOwnerUUID().equals(wearer.getUUID())) return true;
        return target instanceof Leashable l && l.isLeashed() && l.getLeashHolder() != wearer;
    }

    /** The Grip sight rule toward the target ({@link SetDown#sight}). */
    private static boolean sight(Player wearer, Entity target) {
        return SetDown.sight(wearer.level(), Grips.body(wearer), target.getBoundingBox(), CollisionContext.of(wearer));
    }

    private static void startShip(ServerPlayer wearer, BlockPos block, Direction face) {
        Level level = wearer.level();
        String why = wearerRefusal(wearer);
        if (why == null && !ShipFrames.get().isPlot(level, block)) why = "";
        if (why == null && (!TentaworksConfig.value(TentaworksConfig.GRAB_SHIPS) || !ShipFrames.enabled())) why = "ships_off";
        ShipBody ship = why == null ? ShipFrames.get().body(level, block) : null;
        if (why == null && (ship == null || !ship.alive())) why = "";
        Frame frame = why == null ? ship.frame() : null;
        double mass = why == null ? ship.mass() : -1;
        Vec3 local = Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        Vec3 body = Grips.body(wearer);
        if (why == null && !frame.unitScale()) why = "scale";
        if (why == null && shipHeldByAnyone(ship.id())) why = "held";
        List<ShipBody> parts = why == null ? parts(level, ship) : List.of();
        if (why == null && own(wearer, parts)) why = key(GrabRule.Verdict.OWN);
        if (why == null && !(mass >= 0 && mass <= TentacleRigItem.wornTier(wearer).massCap())) why = "mass";
        if (why == null && body.distanceTo(frame.toWorld(local)) > TentacleRigItem.reach(wearer) + GrabRule.SLACK) why = "too_far";
        if (why == null && !Grips.visible(level, frame.toLocal(body), local, face, true)) why = "no_sight";
        if (why == null && crewed(wearer, frame)) why = "crewed";
        if (why == null && arm(wearer, 2) == HoldArm.NONE) why = key(GrabRule.hands(2));
        if (why == null && claimed(wearer, block, face, frame.toWorld(Vec3.atCenterOf(block)))) why = "claimed";
        if (why != null) {
            refuse(wearer, null, why, mass);
            return;
        }
        Held h = new Held(null, true, HoldArm.BOTH);
        h.ship = ship;
        h.level = level;
        h.block = block.immutable();
        h.face = face;
        h.local = local;
        h.mass = mass;
        h.centre = frame.toWorld(local);
        h.rel = Hold.shipRel(wearer.getEyePosition(), wearer.getViewVector(1.0F), wearer.getYRot(), h.centre,
                Math.max(1.0, TentacleRigItem.reach(wearer) - SHIP_INSIDE));
        h.yaw = wearer.getYRot();
        h.orientation = new Quaterniond(frame.pose().orientation());
        h.tangent = Hold.faceTangent(face, frame.normalToLocal(Hold.basis(wearer.getViewVector(1.0F), h.yaw)[1]));
        h.taken = h.centre;
        h.grabLocal = local;
        Vec3 t = Vec3.atLowerCornerOf(h.tangent.getNormal()).scale(Hold.GRIP_SPREAD);
        h.hands = new ShipHands(local.subtract(t), local.add(t), Vec3.atLowerCornerOf(face.getNormal()), h.user);
        h.parts = parts;
        h.build = ids(parts);
        HOLDS.put(wearer.getUUID(), h);
        heavySpeed(wearer, true);
        listener.accept(new Event(wearer, "start", null, "ship", 2, 0, mass));
        changed(wearer);
        RigServer.letGo(wearer);
    }

    /** Another player is on board (Sable tracks them on it): a Ship with people on it is never moved. */
    private static boolean crewed(ServerPlayer wearer, Frame frame) {
        for (Player p : wearer.level().players()) if (p != wearer && !p.isSpectator() && ShipFrames.get().tracking(p, frame)) return true;
        return false;
    }

    /** That build is all that holds the wearer up, as the server sees the Rig and its valid Grips. */
    private static boolean own(ServerPlayer wearer, List<ShipBody> parts) {
        return holdsUp(wearer, frames(parts), RigServer.state(wearer).carries(), RigServer.grips(wearer));
    }

    /** {@link #holdsUp(Player, List, boolean, Iterable)} for the Ship of {@code frame} alone. */
    public static boolean holdsUp(Player wearer, Frame frame, boolean carries, Iterable<Grip> grips) {
        return holdsUp(wearer, List.of(frame), carries, grips);
    }

    /** {@link GrabRule#holdsUp} for {@code wearer} and the Ships of {@code build}: the feet rest on it when Sable tracks them on one of them. */
    public static boolean holdsUp(Player wearer, List<Frame> build, boolean carries, Iterable<Grip> grips) {
        ShipFrames ships = ShipFrames.get();
        List<Object> on = new ArrayList<>(Grips.SLOTS);
        for (Grip g : grips) {
            if (g == null) continue;
            Frame f = ships.isPlot(wearer.level(), g.block()) ? ships.containing(wearer.level(), g.block()) : null;
            on.add(f == null ? null : f.id());
        }
        Set<Object> ids = new HashSet<>();
        boolean feet = false;
        for (Frame f : build) {
            ids.add(f.id());
            feet |= ships.tracking(wearer, f);
        }
        return GrabRule.holdsUp(ids, carries, feet, on);
    }

    /** The held Ship's build now ({@link ShipBuild}), the held Ship first. */
    private static List<ShipBody> parts(Level level, ShipBody held) {
        return ShipBuild.group(held, Grab::freshBox, box -> ShipFrames.get().bodies(level, box), ShipBody::id);
    }

    /** A Ship's world box from its own block bounds and this tick's pose (Sable's world box lags), else Sable's; null when gone. */
    private static @Nullable AABB freshBox(ShipBody body) {
        if (!body.alive()) return null;
        AABB local = body.localBounds();
        return local == null ? body.worldBounds() : ShipBuild.worldBox(body.frame().pose(), local);
    }

    private static Set<Object> ids(List<ShipBody> parts) {
        Set<Object> out = new LinkedHashSet<>();
        for (ShipBody b : parts) out.add(b.id());
        return out;
    }

    private static List<Frame> frames(List<ShipBody> parts) {
        List<Frame> out = new ArrayList<>(parts.size());
        for (ShipBody b : parts) if (b.alive()) out.add(b.frame());
        return out;
    }

    /**
     * Each tick of a Ship hold, before anything else: the build is found again. When it grew the wearer's Grips on the new Ships let
     * go ({@link RigServer#letGo}); any change is told to the clients, which drop their Grips on it too.
     */
    private static void reweigh(ServerPlayer wearer, Held h) {
        List<ShipBody> parts = parts(h.level, h.ship);
        Set<Object> build = ids(parts);
        boolean grew = !h.build.containsAll(build);
        boolean changed = !build.equals(h.build);
        h.parts = parts;
        h.build = build;
        if (grew) RigServer.letGo(wearer);
        if (changed) changed(wearer);
    }

    private static Vec3 vec(Vector3dc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static void refuse(ServerPlayer wearer, @Nullable Entity target, String why, double mass) {
        listener.accept(new Event(wearer, "refuse", target, why, 0, 0, mass));
        reply(wearer, why.isEmpty() ? "" : TentaworksLang.refusalKey(TentaworksLang.GRAB, why));
    }

    private static void throwAlong(ServerPlayer wearer, Vec3 look) {
        if (!holding(wearer)) {
            reply(wearer, "");
            return;
        }
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z) || look.lengthSqr() < 1.0E-6) look = wearer.getViewVector(1.0F);
        double speed = Actuators.throwSpeed(RigAir.worn(wearer));
        Vec3 velocity = look.normalize().scale(speed);
        RigAir.pay(wearer, Actuators.throwCost(wearer));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(wearer, ActuatorFxPacket.thrown(wearer.getId(), velocity));
        release(wearer, "throw", velocity);
        RigEvents.fire(wearer, RigEvents.THROW, speed);
    }

    /**
     * The Grab key tapped with a set-down spot ({@code feet}, the client's): a held mob is carried there and set down. The spot is
     * checked as the client found it ({@link SetDown#check}, Reach + {@value GrabRule#SLACK}); failing that the server's own look
     * picks one ({@link SetDown#spot}); with no valid spot it is set down where it hangs. A Ship is released as by RELEASE.
     */
    private static void place(ServerPlayer wearer, Vec3 feet) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null || h.ship != null || h.placeTo != null) {
            if (h == null || h.ship != null) onPacket(wearer, RigGrabPacket.release());
            return;
        }
        Entity t = h.target;
        Level level = wearer.level();
        double reach = TentacleRigItem.reach(wearer);
        SetDown.Spot spot = Double.isFinite(feet.x) && Double.isFinite(feet.y) && Double.isFinite(feet.z) ? SetDown.check(level, wearer, t, feet, reach + GrabRule.SLACK) : null;
        if (spot == null || !spot.valid()) spot = SetDown.spot(level, wearer, t, wearer.getEyePosition(), wearer.getViewVector(1.0F), reach, GrabRule.SLACK);
        if (spot == null || !spot.valid()) {
            release(wearer, "release", null);
            return;
        }
        h.placeFrom = t.position();
        h.placeTo = spot.feet();
        h.placeTicks = 0;
        h.seatEye = wearer.getEyePosition();
        h.seatLook = wearer.getViewVector(1.0F);
        listener.accept(new Event(wearer, "place", t, "spot", h.heavy ? 2 : 1, 0, -1));
    }

    /**
     * The wheel with the Grab key held: the hold moves {@code notches} × {@value Hold#NOTCH} along the look, clamped ({@link Hold#distance};
     * a Ship {@link Hold#shipWheel}, past its farthest spot to Overhead and back).
     */
    private static void distance(ServerPlayer wearer, int notches) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null || notches == 0 || h.placeTo != null) return;
        double reach = TentacleRigItem.reach(wearer);
        if (h.ship != null) {
            if (h.rel == null) return;
            Hold.ShipHold to = Hold.shipWheel(new Hold.ShipHold(h.rel, h.overhead), notches, Math.max(1.0, reach - SHIP_INSIDE));
            if (to.overhead() != h.overhead) {
                h.lastAim = null;
                h.correction = Vec3.ZERO;
            }
            h.rel = to.rel();
            h.overhead = to.overhead();
        } else {
            h.distance = Hold.distance(h.distance, notches, h.target.getBbWidth(), wearer.getYRot(), reach);
        }
        listener.accept(new Event(wearer, "distance", h.target, String.format(Locale.ROOT, "%.2f", distance(wearer)), h.heavy ? 2 : 1, 0, h.mass));
        changed(wearer);
    }

    /**
     * A Turn of the held Ship in the wearer's view, degrees; a Ship only, at most {@value #TURNS_PER_TICK} a tick, each clamped, into
     * the bank the hold turns from tick by tick ({@link Hold#turnBank}, {@link Hold#turnStep}).
     */
    private static void turn(ServerPlayer wearer, double dYaw, double dPitch) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null || h.ship == null || !Double.isFinite(dYaw) || !Double.isFinite(dPitch)) return;
        int now = wearer.server.getTickCount();
        if (h.turnTick != now) {
            h.turnTick = now;
            h.turns = 0;
        }
        if (++h.turns > TURNS_PER_TICK) return;
        h.bank = Hold.turnBank(h.bank, dYaw, dPitch);
    }

    /**
     * Ends the wearer's hold, if any: the target is set down where it is ({@code velocity} null) or flung with {@code velocity}
     * (blocks per tick). {@code why} names the cause for logs and events; only "release" (the Grab key) seats the target.
     * True when something was held.
     */
    public static boolean release(ServerPlayer wearer, String why, @Nullable Vec3 velocity) {
        Held h = HOLDS.remove(wearer.getUUID());
        if (h == null) return false;
        if (h.heavy) heavySpeed(wearer, false);
        if (h.ship != null) {
            if (h.ship.alive()) h.ship.velocity(velocity == null ? Vec3.ZERO : velocity.scale(20), Vec3.ZERO);
            if (velocity == null && h.ship.alive() && h.level == wearer.level())
                RigEvents.fire(wearer, RigEvents.SHIP_GRAB, h.ship.frame().toWorld(h.grabLocal).distanceTo(h.taken));
        } else if (!h.target.isRemoved()) {
            Entity t = h.target;
            t.resetFallDistance();
            if (velocity != null) {
                t.removeData(TentaworksAttachments.PINNED);
                t.setDeltaMovement(velocity);
                t.hasImpulse = true;
                t.hurtMarked = true;
                Impact.thrown(wearer, t, velocity);
            } else {
                if (t instanceof Mob) t.setData(TentaworksAttachments.PINNED, new Pinned(wearer.getUUID(), t.level().getGameTime() + CALM_TICKS));
                if ("release".equals(why)) seat(wearer, t, h.seatEye == null ? wearer.getEyePosition() : h.seatEye,
                        h.seatLook == null ? wearer.getViewVector(1.0F) : h.seatLook);
            }
        }
        listener.accept(new Event(wearer, velocity != null ? "throw" : "release", h.target, why, h.heavy ? 2 : 1, velocity == null ? 0 : velocity.length(), h.mass));
        changed(wearer);
        return true;
    }

    /** A set-down target over a seat, boat or minecart (under the crosshair from {@code eye} along {@code look}, or under or touching the target) takes it. */
    private static void seat(ServerPlayer wearer, Entity target, Vec3 eye, Vec3 look) {
        Level level = wearer.level();
        if (target.isPassenger() || target.isVehicle()) return;
        Vec3 end = eye.add(look.scale(ActuatorReach.pickRange(TentacleRigItem.reach(wearer))));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, wearer));
        Vec3 at = block.getType() == HitResult.Type.MISS ? null : ShipFrames.get().worldAt(level, block);
        Vec3 to = at == null || !ActuatorReach.segment(eye, at, "seat sweep") ? end : at;
        EntityHitResult aimed = ProjectileUtil.getEntityHitResult(wearer, eye, to, wearer.getBoundingBox().expandTowards(to.subtract(eye)).inflate(1.0),
                Grab::seatEntity, eye.distanceToSqr(to));
        if (aimed != null && target.startRiding(aimed.getEntity())) return;
        if (block.getType() == HitResult.Type.BLOCK && seatBlock(level, block.getBlockPos(), target)) return;
        for (Entity v : level.getEntities(target, target.getBoundingBox().inflate(0.25).expandTowards(0, -1, 0), Grab::seatEntity))
            if (target.startRiding(v)) return;
        BlockPos feet = target.blockPosition();
        if (!seatBlock(level, feet, target)) seatBlock(level, feet.below(), target);
    }

    private static boolean seatEntity(Entity e) {
        return e instanceof Boat || e instanceof AbstractMinecart;
    }

    private static boolean seatBlock(Level level, BlockPos pos, Entity target) {
        if (!(level.getBlockState(pos).getBlock() instanceof SeatBlock) || SeatBlock.isSeatOccupied(level, pos) || !SeatBlock.canBePickedUp(target)) return false;
        SeatBlock.sitDown(level, pos, target);
        return target.isPassenger();
    }

    /** Walking and Rig Speed at {@code grab.heavySpeedFactor} while a heavy target is held (a transient modifier, never saved). */
    private static void heavySpeed(ServerPlayer wearer, boolean on) {
        RigServer.resetSpeedWindow(wearer);
        AttributeInstance speed = wearer.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(HEAVY_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(HEAVY_ID, heavyFactor() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static ActuatorStatePacket state(ServerPlayer wearer, String refusal) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null) return ActuatorStatePacket.empty(wearer.getId(), refusal);
        h.told = (float) distance(wearer);
        List<UUID> build = new ArrayList<>(h.build.size());
        for (Object id : h.build) if (id instanceof UUID u) build.add(u);
        return new ActuatorStatePacket(wearer.getId(), h.target == null ? -1 : h.target.getId(), h.heavy, h.ship == null ? null : h.block,
                h.ship == null ? Direction.UP : h.face, refusal, h.arm, h.told, h.ship != null && h.overhead, h.tangent,
                tip(h, ShipHands.LEFT), tip(h, ShipHands.RIGHT), List.copyOf(build));
    }

    /** Tip {@code i} of a held Ship for the clients, relative to the held block; {@link ActuatorStatePacket.Tip#NONE} for a mob. */
    private static ActuatorStatePacket.Tip tip(Held h, int i) {
        if (h.hands == null) return ActuatorStatePacket.Tip.NONE;
        ShipHands.Tip t = h.hands.tip(i);
        Vec3 base = Vec3.atLowerCornerOf(h.block);
        return new ActuatorStatePacket.Tip(t.at().subtract(base), t.from().subtract(base), t.reach());
    }

    /** To the wearer only: the current hold and a refusal key ("" none). */
    private static void reply(ServerPlayer wearer, String refusal) {
        if (wearer.connection != null) PacketDistributor.sendToPlayer(wearer, state(wearer, refusal));
    }

    private static void changed(ServerPlayer wearer) {
        ActuatorStatePacket p = state(wearer, "");
        if (wearer.connection != null) PacketDistributor.sendToPlayer(wearer, p);
        PacketDistributor.sendToPlayersTrackingEntity(wearer, p);
    }

    private static void tick(MinecraftServer server) {
        if (HOLDS.isEmpty()) return;
        for (Map.Entry<UUID, Held> e : new ArrayList<>(HOLDS.entrySet())) {
            ServerPlayer wearer = server.getPlayerList().getPlayer(e.getKey());
            Held h = e.getValue();
            if (wearer == null) {
                HOLDS.remove(e.getKey());
                if (h.ship != null && h.ship.alive()) h.ship.velocity(Vec3.ZERO, Vec3.ZERO);
                continue;
            }
            if (h.ship != null && h.ship.alive()) reweigh(wearer, h);
            String why = Hold.ends(status(wearer, h));
            if (why != null) {
                release(wearer, why, null);
                continue;
            }
            if (h.ship != null) holdShip(wearer, h);
            else holdMob(wearer, h);
            if (HOLDS.get(e.getKey()) == h && Math.abs(distance(wearer) - h.told) >= DISTANCE_TOLD) changed(wearer);
            RigAir.pay(wearer, Actuators.grabPerTick(wearer));
        }
    }

    private static Hold.Status status(ServerPlayer wearer, Held h) {
        boolean gone = h.ship != null ? !h.ship.alive() : h.target.isRemoved() || !h.target.isAlive() || h.target.isPassenger();
        Level level = h.ship != null ? h.level : h.target.level();
        boolean full = h.arm.uses(InteractionHand.MAIN_HAND) && !wearer.getMainHandItem().isEmpty()
                || h.arm.uses(InteractionHand.OFF_HAND) && !wearer.getOffhandItem().isEmpty();
        boolean underfoot = h.ship != null && !gone && level == wearer.level() && holdsUp(wearer, frames(h.parts), RigServer.state(wearer).carries(), RigServer.grips(wearer));
        return new Hold.Status(wearer.isAlive(), Actuators.fitted(wearer), RigAir.has(wearer), RigServer.state(wearer) == RigState.CRAWL, gone,
                level == wearer.level(), h.far, full, underfoot);
    }

    private static void holdMob(ServerPlayer wearer, Held h) {
        Entity t = h.target;
        Level level = wearer.level();
        Vec3 to;
        if (h.placeTo != null) {
            h.placeTicks++;
            Vec3 aim = h.placeFrom.lerp(h.placeTo, Math.min(1.0, h.placeTicks / (double) SetDown.CARRY_TICKS));
            to = Hold.sweep(level, t, aim);
            h.centre = aim.add(0, t.getBbHeight() / 2, 0);
        } else {
            Vec3 eye = wearer.getEyePosition(), look = wearer.getViewVector(1.0F);
            float yaw = wearer.getYRot();
            int side = Hold.side(h.arm.side(wearer.getMainArm()));
            double d = Hold.fit(eye, look, yaw, t.getBbWidth(), t.getBbHeight(), side, h.distance, Grips.body(wearer), TentacleRigItem.reach(wearer));
            h.centre = Hold.point(eye, look, yaw, t.getBbWidth(), t.getBbHeight(), side, d);
            to = Hold.sweep(level, t, Hold.place(level, t, h.centre.subtract(0, t.getBbHeight() / 2, 0)));
        }
        t.setPos(to.x, to.y, to.z);
        t.setDeltaMovement(Vec3.ZERO);
        t.resetFallDistance();
        if (t instanceof Mob mob) mob.getNavigation().stop();
        ((ServerLevel) level).getChunkSource().broadcast(t, new ClientboundTeleportEntityPacket(t));
        SENT.put(t, t.position());
        far(wearer, h, t.getBoundingBox().getCenter());
        if (h.placeTo != null && h.placeTicks >= SetDown.CARRY_TICKS) release(wearer, "release", null);
    }

    private static void far(ServerPlayer wearer, Held h, Vec3 at) {
        if (Hold.far(Grips.body(wearer).distanceTo(at), TentacleRigItem.reach(wearer))) h.far++;
        else h.far = 0;
    }

    /**
     * Drives the held Ship so its pivot (the midpoint of the tips, {@link ShipHands}) sits where the held face was in the wearer's
     * view (or Overhead), turned by the wearer's Turn (from the bank, at half rate while a tip reaches) and then by their yaw since
     * the start. When a tip takes hold the pivot moves: the hold's correction keeps the new pivot where it was this tick and settles
     * back to the hold over a few ticks ({@link Hold#correction}, {@link Hold#settle}), so the Ship never jumps.
     */
    private static void holdShip(ServerPlayer wearer, Held h) {
        Frame f = h.ship.frame();
        float yaw = wearer.getYRot();
        double[] step = Hold.turnStep(h.bank, h.hands.reaching());
        if (step[0] != 0 || step[1] != 0) {
            h.user = Hold.shipTurnInput(h.user, h.yaw, step[0], step[1]).normalize();
            h.bank = new double[] {h.bank[0] - step[0], h.bank[1] - step[1]};
        }
        Quaterniond want = Hold.shipWant(h.orientation, yaw - h.yaw, h.user);
        double reach = TentacleRigItem.reach(wearer);
        Vec3[] shoulders = {shoulder(wearer, ShipHands.LEFT), shoulder(wearer, ShipHands.RIGHT)};
        ShipHands.Change change = h.hands.tick(f.pose(), h.user, shoulders, hullCentre(h, f), Hold.yawBasis(yaw)[1], hullRay(h), reach);
        if (change == ShipHands.Change.LET_GO) {
            for (int i = 0; i < 2; i++)
                if (!h.hands.tip(i).holding()) listener.accept(new Event(wearer, "regrip", null, i == ShipHands.LEFT ? "left" : "right", 2, 0, h.mass));
        }
        if (change == ShipHands.Change.TOOK_HOLD) {
            Vec3 local = h.hands.pivot();
            Vec3 was = h.lastAim == null ? f.toWorld(local) : Hold.shipWorld(h.lastAim, h.lastWant, local, f.pose().pivot());
            h.local = local;
            h.correction = Hold.correction(holdBasis(wearer, h, yaw), base(wearer, h, want, yaw), was);
        }
        if (change != ShipHands.Change.NONE) changed(wearer);
        Vec3 point = Hold.corrected(holdBasis(wearer, h, yaw), base(wearer, h, want, yaw), h.correction);
        h.correction = Hold.settle(h.correction);
        Vec3 aim = Hold.shipAim(point, want, h.local, f.pose().pivot());
        Vec3 feed = h.lastAim == null ? Vec3.ZERO : aim.subtract(h.lastAim).scale(20);
        Vec3 spin = h.lastWant == null ? Vec3.ZERO : rotation(new Quaterniond(want).mul(new Quaterniond(h.lastWant).conjugate())).scale(20);
        h.lastAim = aim;
        h.lastWant = want;
        Vec3 v = feed.add(aim.subtract(vec(f.pose().position())).scale(SHIP_GAIN));
        if (v.length() > SHIP_MAX_SPEED) v = v.normalize().scale(SHIP_MAX_SPEED);
        Vec3 err = rotation(new Quaterniond(want).mul(new Quaterniond(f.pose().orientation()).conjugate()));
        h.ship.velocity(v, err.scale(SHIP_GAIN).add(spin));
        h.centre = f.toWorld(h.local);
        far(wearer, h, nearestTip(h, f, Grips.body(wearer)));
    }

    /** The held Ship is Overhead this tick: Overhead asked and its bounds known. */
    private static boolean overheadNow(Held h) {
        return h.overhead && h.ship.localBounds() != null;
    }

    /** The basis the hold's correction is kept in: the camera's in front, the level of the wearer's yaw Overhead. */
    private static Vec3[] holdBasis(ServerPlayer wearer, Held h, float yaw) {
        return overheadNow(h) ? Hold.yawBasis(yaw) : Hold.basis(wearer.getViewVector(1.0F), yaw);
    }

    /** Where the hold puts the pivot this tick, before its correction: in front of the eye ({@link Hold#shipPoint}) or Overhead. */
    private static Vec3 base(ServerPlayer wearer, Held h, Quaterniondc want, float yaw) {
        Vec3 point = h.overhead ? overhead(wearer, h, want, yaw) : null;
        return point != null ? point : Hold.shipPoint(wearer.getEyePosition(), wearer.getViewVector(1.0F), yaw, h.rel);
    }

    /** Where the Actuator of tip {@code i} sits on the wearer ({@link Hold#shoulder}). */
    private static Vec3 shoulder(ServerPlayer wearer, int i) {
        return Hold.shoulder(wearer.position(), wearer.getYRot(), wearer.getBbHeight(), ShipHands.side(i));
    }

    /** The middle of the held Ship's blocks in the world, else its body's centre. */
    private static Vec3 hullCentre(Held h, Frame f) {
        AABB b = h.ship.localBounds();
        return b == null ? vec(f.pose().position()) : f.toWorld(b.getCenter());
    }

    /** The holding tip nearest {@code body} (world): the far rule measures the hold there. */
    private static Vec3 nearestTip(Held h, Frame f, Vec3 body) {
        Vec3 best = null;
        for (int i = 0; i < 2; i++) {
            ShipHands.Tip t = h.hands.tip(i);
            if (!t.holding()) continue;
            Vec3 w = f.toWorld(t.at());
            if (best == null || w.distanceToSqr(body) < best.distanceToSqr(body)) best = w;
        }
        return best == null ? h.centre : best;
    }

    /** The first block of the held Ship on a plot-space segment, in plot space only (no world block, no other Ship). */
    private static ShipHands.Ray hullRay(Held h) {
        Level level = h.level;
        return (from, to) -> BlockGetter.traverseBlocks(from, to, (Void) null, (c, pos) -> {
            if (!level.isLoaded(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return null;
            VoxelShape shape = state.getCollisionShape(level, pos);
            BlockHitResult hit = shape.isEmpty() ? null : shape.clip(from, to, pos);
            if (hit == null) return null;
            Frame on = ShipFrames.get().containing(level, pos);
            return on == null || !on.id().equals(h.ship.id()) ? null : new ShipHands.Hit(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
        }, c -> null);
    }

    /**
     * Where the held face goes Overhead ({@link Hold#shipOverhead}): the hull's lowest corner below the face in the wanted
     * orientation, from the Ship's own bounds (its world box lags a tick or two, and a hold measured on it bounces). Null when
     * those bounds are unknown: the Ship stays in front rather than over the wearer's head.
     */
    private static @Nullable Vec3 overhead(ServerPlayer wearer, Held h, Quaterniondc want, float yaw) {
        AABB local = h.ship.localBounds();
        if (local == null) return null;
        double below = Hold.shipBelow(want, h.local, local);
        return Hold.shipOverhead(wearer.getEyePosition(), yaw, below, Grips.body(wearer), Math.max(1.0, TentacleRigItem.reach(wearer) - SHIP_INSIDE));
    }

    /** The rotation vector of {@code q} (axis × angle, radians), the shorter way round. */
    private static Vec3 rotation(Quaterniond q) {
        double sign = q.w < 0 ? -1 : 1;
        Vec3 axis = new Vec3(q.x * sign, q.y * sign, q.z * sign);
        double len = axis.length();
        return axis.scale(len < 1.0E-9 ? 2 : 2 * Math.atan2(len, Math.abs(q.w)) / len);
    }
}
