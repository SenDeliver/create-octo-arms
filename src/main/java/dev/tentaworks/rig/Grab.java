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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The Left Actuator's Grab and Throw, server side (adr/0007: the server is the authority, the client only asks with
 * {@link RigGrabPacket} and draws what {@link ActuatorStatePacket} tells it). START takes a mob under the wearer's crosshair:
 * fitted Actuators with Air, an empty main hand, at most one start per {@value #START_EVERY} ticks, the target rules
 * ({@link GrabRule}) and then the loader's entity-interaction event, so claim and protection mods can refuse. The mob is then
 * Pinned ({@link #pinned}) and swept to the hold point ({@link Hold}) every tick; a heavy one takes both Actuators and slows the
 * wearer to {@code grab.heavySpeedFactor}. Holding pays {@code air.grab}, a Throw {@code air.throw}.
 * Ship Grab (Sable): SHIP takes the Ship owning a plot block with both Actuators; its body velocity drives it to the wearer's
 * body plus the offset it had at the start, rotation kept ({@value #SHIP_GAIN}/s of the error plus the wearer's move, which also
 * cancels gravity). Grips on it do not hold the wearer up while held.
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
    private static final ResourceLocation HEAVY_ID = TentaworksIds.id(TentaworksIds.ACTUATOR_HEAVY);
    private static final Map<UUID, Held> HOLDS = new HashMap<>();
    private static final Map<UUID, Integer> STARTED = new HashMap<>();
    private static boolean asking;
    private static Consumer<Event> listener = e -> {
    };

    /** One start, refusal, release or throw (dev trace): {@code why} is the refusal or release reason, {@code speed} the throw's. */
    public record Event(ServerPlayer wearer, String kind, @Nullable Entity target, String why, int actuators, double speed, double mass) {
    }

    private static final class Held {
        final Entity target;
        final boolean heavy;
        int far;
        Vec3 centre;
        ShipBody ship;
        Level level;
        BlockPos block;
        Direction face;
        Vec3 local;
        Vec3 offset;
        Quaterniond orientation;
        Vec3 lastBody;
        double mass = -1;
        Vec3 taken;

        Held(@Nullable Entity target, boolean heavy) {
            this.target = target;
            this.heavy = heavy;
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
            if (e.getEntity() instanceof ServerPlayer p && e.getTarget() == target(p)) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) release(p, "stop", null);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            HOLDS.clear();
            STARTED.clear();
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
        }
    }

    public static boolean holding(ServerPlayer wearer) {
        return HOLDS.containsKey(wearer.getUUID());
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

    /** A Grip on the Ship this wearer holds: it does not carry the wearer. */
    public static boolean onHeldShip(ServerPlayer wearer, Grip grip) {
        Held h = HOLDS.get(wearer.getUUID());
        if (h == null || h.ship == null || !ShipFrames.get().isPlot(wearer.level(), grip.block())) return false;
        Frame f = ShipFrames.get().containing(wearer.level(), grip.block());
        return f != null && f.id().equals(h.ship.id());
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
        if (!RigAir.has(wearer)) return "no_air";
        return wearer.getMainHandItem().isEmpty() ? null : "hand";
    }

    private static void start(ServerPlayer wearer, int id) {
        Entity target = wearer.level().getEntity(id);
        String why = wearerRefusal(wearer);
        GrabRule.Verdict verdict = null;
        if (why == null) {
            verdict = GrabRule.check(facts(wearer, target), new GrabRule.Rules(TentacleRigItem.reach(wearer), TentaworksConfig.value(TentaworksConfig.GRAB_TAMED_OTHERS)));
            if (!verdict.ok()) why = verdict == GrabRule.Verdict.GONE ? "" : verdict.name().toLowerCase(Locale.ROOT);
        }
        if (why == null && claimed(wearer, target)) why = "claimed";
        if (why != null) {
            refuse(wearer, target, why, -1);
            return;
        }
        Held h = new Held(target, verdict == GrabRule.Verdict.HEAVY);
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
        listener.accept(new Event(wearer, "start", target, verdict.name().toLowerCase(Locale.ROOT), verdict.actuators(), 0, -1));
        changed(wearer);
        if (target instanceof Blaze) RigEvents.fire(wearer, RigEvents.GRAB_BLAZE, 1);
    }

    /** The loader's entity-interaction event, as an empty-handed use on the target: cancelled means a claim or protection mod said no. */
    private static boolean claimed(ServerPlayer wearer, Entity target) {
        return GrabRule.claimed(true, () -> ask(() -> CommonHooks.onInteractEntity(wearer, target, InteractionHand.MAIN_HAND) != null));
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
        if (target == null || target.isRemoved() || !target.isAlive() || target.isSpectator())
            return new GrabRule.Target(false, true, false, false, false, false, 0, 0, 0, false, Double.MAX_VALUE, false);
        boolean boss = target.getType().is(BOSSES) || target instanceof EnderDragon || target instanceof WitherBoss;
        return new GrabRule.Target(target instanceof LivingEntity, false, target instanceof Player, target.isPassenger() || target.isVehicle(),
                heldByAnyone(target), boss, target instanceof LivingEntity l ? l.getMaxHealth() : 0, target.getBbWidth(), target.getBbHeight(),
                othersAnimal(wearer, target), Grips.body(wearer).distanceTo(target.getBoundingBox().getCenter()), sight(wearer, target));
    }

    /** Tamed or owned by another player, or leashed to anything but the wearer (a fence knot belongs to someone too). */
    private static boolean othersAnimal(Player wearer, Entity target) {
        if (target instanceof OwnableEntity own && own.getOwnerUUID() != null && !own.getOwnerUUID().equals(wearer.getUUID())) return true;
        return target instanceof Leashable l && l.isLeashed() && l.getLeashHolder() != wearer;
    }

    /** The Grip sight rule toward the target: no block on the line from the body to the centre of its box before the line enters it. */
    private static boolean sight(Player wearer, Entity target) {
        Vec3 body = Grips.body(wearer);
        AABB box = target.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 entry = box.clip(body, centre).orElse(body);
        BlockHitResult hit = wearer.level().clip(new ClipContext(body, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wearer));
        return hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(body) >= entry.distanceToSqr(body) - 1.0E-4;
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
        if (why == null && own(wearer, frame)) why = "own";
        if (why == null && !(mass >= 0 && mass <= TentacleRigItem.wornTier(wearer).massCap())) why = "mass";
        if (why == null && body.distanceTo(frame.toWorld(local)) > TentacleRigItem.reach(wearer) + GrabRule.SLACK) why = "too_far";
        if (why == null && !Grips.visible(level, frame.toLocal(body), local, face, true)) why = "no_sight";
        if (why == null && crewed(wearer, frame)) why = "crewed";
        if (why == null && claimed(wearer, block, face, frame.toWorld(Vec3.atCenterOf(block)))) why = "claimed";
        if (why != null) {
            refuse(wearer, null, why, mass);
            return;
        }
        Held h = new Held(null, true);
        h.ship = ship;
        h.level = level;
        h.block = block.immutable();
        h.face = face;
        h.local = local;
        h.mass = mass;
        h.offset = vec(frame.pose().position()).subtract(body);
        h.orientation = new Quaterniond(frame.pose().orientation());
        h.centre = frame.toWorld(local);
        h.taken = h.centre;
        HOLDS.put(wearer.getUUID(), h);
        heavySpeed(wearer, true);
        listener.accept(new Event(wearer, "start", null, "ship", 2, 0, mass));
        changed(wearer);
    }

    /** Another player is on board (Sable tracks them on it): a Ship with people on it is never moved. */
    private static boolean crewed(ServerPlayer wearer, Frame frame) {
        for (Player p : wearer.level().players()) if (p != wearer && !p.isSpectator() && ShipFrames.get().tracking(p, frame)) return true;
        return false;
    }

    /** The wearer stands on that Ship or it is their Carry Ship: it is what holds them up. */
    private static boolean own(ServerPlayer wearer, Frame frame) {
        return ShipFrames.get().tracking(wearer, frame) || frame.id().equals(RigServer.carryShip(wearer));
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
                RigEvents.fire(wearer, RigEvents.SHIP_GRAB, h.ship.frame().toWorld(h.local).distanceTo(h.taken));
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
                if ("release".equals(why)) seat(wearer, t);
            }
        }
        listener.accept(new Event(wearer, velocity != null ? "throw" : "release", h.target, why, h.heavy ? 2 : 1, velocity == null ? 0 : velocity.length(), h.mass));
        changed(wearer);
        return true;
    }

    /** A set-down target over a seat, boat or minecart (under the crosshair, or under or touching the target) takes it. */
    private static void seat(ServerPlayer wearer, Entity target) {
        Level level = wearer.level();
        if (target.isPassenger() || target.isVehicle()) return;
        Vec3 eye = wearer.getEyePosition();
        Vec3 end = eye.add(wearer.getViewVector(1.0F).scale(TentacleRigItem.reach(wearer)));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, wearer));
        Vec3 to = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
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
        return new ActuatorStatePacket(wearer.getId(), h.target == null ? -1 : h.target.getId(), h.heavy, h.ship == null ? null : h.block,
                h.ship == null ? Direction.UP : h.face, refusal);
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
            String why = Hold.ends(status(wearer, h));
            if (why != null) {
                release(wearer, why, null);
                continue;
            }
            if (h.ship != null) holdShip(wearer, h);
            else holdMob(wearer, h);
            RigAir.pay(wearer, Actuators.grabPerTick(wearer));
        }
    }

    private static Hold.Status status(ServerPlayer wearer, Held h) {
        boolean gone = h.ship != null ? !h.ship.alive() : h.target.isRemoved() || !h.target.isAlive() || h.target.isPassenger();
        Level level = h.ship != null ? h.level : h.target.level();
        return new Hold.Status(wearer.isAlive(), Actuators.fitted(wearer), RigAir.has(wearer), RigServer.state(wearer) == RigState.CRAWL, gone,
                level == wearer.level(), h.far);
    }

    private static void holdMob(ServerPlayer wearer, Held h) {
        Entity t = h.target;
        Level level = wearer.level();
        h.centre = Hold.point(wearer.getEyePosition(), wearer.getViewVector(1.0F), wearer.getYRot(), t.getBbWidth(), t.getBbHeight());
        Vec3 to = Hold.sweep(level, t, Hold.place(level, t, h.centre.subtract(0, t.getBbHeight() / 2, 0)));
        t.setPos(to.x, to.y, to.z);
        t.setDeltaMovement(Vec3.ZERO);
        t.resetFallDistance();
        if (t instanceof Mob mob) mob.getNavigation().stop();
        ((ServerLevel) level).getChunkSource().broadcast(t, new ClientboundTeleportEntityPacket(t));
        far(wearer, h, t.getBoundingBox().getCenter());
    }

    private static void far(ServerPlayer wearer, Held h, Vec3 at) {
        if (Hold.far(Grips.body(wearer).distanceTo(at), TentacleRigItem.reach(wearer))) h.far++;
        else h.far = 0;
    }

    /** Drives the held Ship to the wearer's body plus its offset, its rotation as at the start. */
    private static void holdShip(ServerPlayer wearer, Held h) {
        Frame f = h.ship.frame();
        Vec3 body = Grips.body(wearer);
        Vec3 feed = h.lastBody == null ? Vec3.ZERO : body.subtract(h.lastBody).scale(20);
        h.lastBody = body;
        Vec3 v = feed.add(body.add(h.offset).subtract(vec(f.pose().position())).scale(SHIP_GAIN));
        if (v.length() > SHIP_MAX_SPEED) v = v.normalize().scale(SHIP_MAX_SPEED);
        Quaterniond err = new Quaterniond(h.orientation).mul(new Quaterniond(f.pose().orientation()).conjugate());
        double sign = err.w < 0 ? -1 : 1;
        Vec3 axis = new Vec3(err.x * sign, err.y * sign, err.z * sign);
        double len = axis.length();
        double k = len < 1.0E-9 ? 2 : 2 * Math.atan2(len, Math.abs(err.w)) / len;
        h.ship.velocity(v, axis.scale(k * SHIP_GAIN));
        h.centre = f.toWorld(h.local);
        far(wearer, h, h.centre);
    }
}
