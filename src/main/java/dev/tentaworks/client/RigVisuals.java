package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.network.RigStatePacket;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.Tentacles;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import org.joml.Vector3f;

/**
 * What every client draws for each Rig it sees: the wearer's own gait output and other players' relayed Tentacles go
 * through the same pipe. Updated once per client tick (previous and current values, interpolated with partialTick when drawn).
 * A Grip on a Ship is in its plot coords: each point is kept in the frame it sits on (the target's Ship, re-resolved every tick)
 * and taken into the world with the Ship's render pose when drawn, so tips stay on a moving hull.
 * The Rig's own motion (whir, height hiss) is measured in the frame of the Ship holding most of its Grips (at least two):
 * a Rig riding a Ship idle is still.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class RigVisuals {
    /** Farthest a Grip may be from its wearer to be drawn: reach, a Ship's carry and lag. */
    static final double MAX_GRIP_DISTANCE = TentacleRigItem.MAX_REACH * 2;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long lastDropLog;

    private static final float DEPLOY_PER_TICK = 0.125F;
    private static final Int2ObjectOpenHashMap<Visual> VISUALS = new Int2ObjectOpenHashMap<>();
    private static final int[] PLANTS_NEAR = new int[2];
    private static final int[] PLANT_SOUNDS = new int[2];

    public static final class Slot {
        /** Ticks a drawn tip stays a trustworthy start for the next arc (a Rig off screen is not drawn). */
        static final int DRAWN_FRESH = 2;
        /** Ticks over which a Tentacle that lost its Grip lets go of the pad pose. */
        static final float RELEASE_PER_TICK = 0.25F;
        /** A tick count no tick has: "never happened", far enough from 0 that subtractions do not overflow. */
        static final int NEVER = -1_000_000;

        Grip target;
        Frame frame;
        Vec3 from;
        Vec3 fromNormal;
        Frame fromFrame;
        final StepPhase step = new StepPhase();
        /** Last tip and pad normal drawn (world), and when: the arc of a new Step starts where the Tentacle is, not where it was. */
        Vec3 drawnTip, drawnNormal;
        int drawnTick = NEVER;
        /** Where the pad was when the Grip went away (world), blended out over {@link #RELEASE_PER_TICK}. */
        Vec3 lastTip, lastNormal;
        float release, releaseO;
        /** A Step that started from nothing to hold does not count as a plant (Activation). */
        boolean silent;
        /** Elbow push along its pole, signed (-1..1; the far side when the near side is in a block), one value per tick. */
        float elbow = 1, elbowO = 1;
        int elbowTick = NEVER;
        /** Free pose spring: the hanging tip's offset from its shoulder (world axes) follows the rest offset with a little lag. */
        final Vector3f freePos = new Vector3f(), freePosO = new Vector3f(), freeVel = new Vector3f();
        /** The shoulder's world position when the free pose was last stepped. */
        final Vector3f freeShoulder = new Vector3f();
        int freeTick = NEVER;

        public Grip target() {
            return target;
        }

        public float phase(float pt) {
            return step.at(pt);
        }

        public float release(float pt) {
            return releaseO + (release - releaseO) * pt;
        }

        /** The target point in world coords (Ship render pose), or null. */
        public Vec3 point(float pt) {
            return target == null ? null : toWorld(frame, target.point(), pt);
        }

        /**
         * Tip on the arc from where the Tentacle was (its last Grip, or {@code free}: the free pose) to the target, world coords.
         * {@code body} is the wearer's centre: the arc bows toward it, away from blocks.
         */
        public Vec3 tip(float pt, Vec3 free, Vec3 body) {
            if (target == null) return null;
            float ph = step.at(pt);
            Vec3 a = from != null ? toWorld(fromFrame, from, pt) : free;
            Vec3 b = point(pt);
            if (ph >= 1 || a == null) return b;
            Vec3 nA = from == null || fromNormal == null ? null : normalToWorld(fromFrame, fromNormal, pt);
            return TentacleMath.arc(a, b, body, nA, targetNormal(pt), smooth(ph));
        }

        /** Pad normal, world coords. */
        public Vec3 normal(float pt, Vec3 body) {
            Vec3 to = targetNormal(pt);
            if (from == null || fromNormal == null || step.at(pt) >= 1) return to;
            Vec3 nA = normalToWorld(fromFrame, fromNormal, pt);
            Vec3 a = toWorld(fromFrame, from, pt);
            Vec3 q = TentacleMath.liftAxis(nA, to, a.add(point(pt)).scale(0.5), body);
            return TentacleMath.normalBetween(nA, to, q, smooth(step.at(pt)));
        }

        Vec3 targetNormal(float pt) {
            return normalToWorld(frame, faceNormal(target.face()), pt);
        }

        /** Called by the renderer: where the Tentacle's pad was drawn this frame (world). */
        void drawn(Vec3 tip, Vec3 normal, int tick) {
            drawnTip = tip;
            drawnNormal = normal;
            drawnTick = tick;
        }

        boolean fresh(int tick) {
            return drawnTip != null && tick - drawnTick <= DRAWN_FRESH;
        }

        /** Where the Tentacle's pad is now (world): drawn this tick or last, else its Grip, else the pad it is letting go of. */
        Vec3 tipNow(int tick) {
            if (fresh(tick)) return drawnTip;
            if (target != null) return point(1);
            return release > 0 ? lastTip : null;
        }

        Vec3 normalNow(int tick) {
            if (fresh(tick)) return drawnNormal;
            if (target != null) return targetNormal(1);
            return release > 0 ? lastNormal : null;
        }
    }

    public static final class Visual {
        final Slot[] slots = new Slot[Tentacles.SLOTS];
        RigState state = RigState.STOWED;
        float deploy, deployO;
        Tentacles pending = Tentacles.NONE;
        RigState pendingState = RigState.STOWED;
        Vec3 lastPos;
        Vec3 lastLocal;
        Object lastRide;
        double speed;
        int vertTicks, stillVertTicks;
        int plants, plantSounds, lifts;
        RigSounds.Whir whir;
        int whirStarted = -1000;
        int lod;

        Visual() {
            for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
        }

        public Slot slot(int i) {
            return slots[i];
        }

        public RigState state() {
            return state;
        }

        public float deploy(float pt) {
            return deployO + (deploy - deployO) * pt;
        }

        /** Smoothed own move per tick: the world move minus the carry Ship's. */
        public double speed() {
            return speed;
        }

        public int plants() {
            return plants;
        }

        public int plantSounds() {
            return plantSounds;
        }

        public int lifts() {
            return lifts;
        }

        public boolean visible() {
            return deploy > 0 || deployO > 0;
        }
    }

    private RigVisuals() {
    }

    /** Step plants within hearing and plant sounds played since start: [own Rig, other players' Rigs]. */
    public static int[] plantTotals() {
        return new int[]{PLANTS_NEAR[0], PLANT_SOUNDS[0], PLANTS_NEAR[1], PLANT_SOUNDS[1]};
    }

    public static Visual get(Entity entity) {
        return VISUALS.get(entity.getId());
    }

    public static Iterable<Int2ObjectOpenHashMap.Entry<Visual>> all() {
        return VISUALS.int2ObjectEntrySet();
    }

    static void local(Player player, RigState state, Tentacles tentacles) {
        Visual v = VISUALS.computeIfAbsent(player.getId(), k -> new Visual());
        v.pending = tentacles;
        v.pendingState = state;
    }

    /** A player this many ticks old on this client (or not here yet) has just come into view: its Active Rig is drawn deployed at once. */
    private static final int JUST_APPEARED = 20;

    static void remote(RigStatePacket packet) {
        Visual v = VISUALS.get(packet.entityId());
        if (v == null) {
            v = new Visual();
            Level level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(packet.entityId());
            if (packet.state() != RigState.STOWED && (entity == null || entity.tickCount < JUST_APPEARED)) {
                v.state = packet.state();
                v.deploy = v.deployO = 1;
            }
            VISUALS.put(packet.entityId(), v);
        }
        v.pending = packet.state() == RigState.STOWED ? Tentacles.NONE : packet.tentacles();
        v.pendingState = packet.state();
    }

    static void clear() {
        VISUALS.values().forEach(v -> {
            if (v.whir != null) v.whir.stopNow();
        });
        VISUALS.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!VISUALS.isEmpty()) clear();
            return;
        }
        if (mc.isPaused()) return;
        var it = VISUALS.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Visual v = e.getValue();
            Entity entity = mc.level.getEntity(e.getIntKey());
            if (!(entity instanceof Player player) || !entity.isAlive()) {
                if (v.whir != null) v.whir.stopNow();
                it.remove();
                continue;
            }
            apply(player, v);
            if (v.state == RigState.STOWED && v.deploy <= 0 && v.deployO <= 0) {
                if (v.whir != null) v.whir.stopNow();
                it.remove();
            }
        }
    }

    private static void apply(Player player, Visual v) {
        RigState was = v.state;
        v.state = v.pendingState;
        boolean out = v.state != RigState.STOWED;
        v.deployO = v.deploy;
        v.deploy = out ? Math.min(1, v.deploy + DEPLOY_PER_TICK) : Math.max(0, v.deploy - DEPLOY_PER_TICK);
        if (was == RigState.STOWED && out) RigSounds.activate(player, true);
        if (was != RigState.STOWED && !out) RigSounds.activate(player, false);
        if (was != v.state && v.state == RigState.FALLING) RigSounds.letGo(player);

        Level level = player.level();
        ShipFrames ships = ShipFrames.get();
        int tick = player.tickCount;
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Slot s = v.slots[i];
            Grip next = v.pending.grips()[i];
            Frame nextFrame = null;
            if (next != null && ships.isPlot(level, next.block()) && (nextFrame = ships.containing(level, next.block())) == null) next = null;
            if (next != null && !plausible(player, toWorld(nextFrame, next.point(), 1))) {
                if (System.currentTimeMillis() - lastDropLog > 1000) {
                    lastDropLog = System.currentTimeMillis();
                    LOG.warn("Grip of {} not drawn: point {} block {} plot={} frame={} is {} blocks from the wearer (max {}); Ship pose missing on this client?",
                            player.getName().getString(), next.point(), next.block(), ships.isPlot(level, next.block()), nextFrame == null ? "none" : nextFrame.id(),
                            (long) toWorld(nextFrame, next.point(), 1).distanceTo(player.position()), (long) MAX_GRIP_DISTANCE);
                }
                next = null;
                nextFrame = null;
            }
            s.releaseO = s.release;
            boolean planted = false;
            if (next == null) {
                if (s.target != null) {
                    s.lastTip = s.tipNow(tick);
                    s.lastNormal = s.normalNow(tick);
                    s.release = s.releaseO = s.lastTip == null ? 0 : 1;
                    if (s.lastTip != null && tick - s.freeTick <= Slot.DRAWN_FRESH) {
                        s.freePos.set((float) s.lastTip.x, (float) s.lastTip.y, (float) s.lastTip.z).sub(s.freeShoulder);
                        s.freePosO.set(s.freePos);
                        s.freeVel.zero();
                        s.freeTick = tick;
                    }
                    s.elbowO = s.elbow;
                } else if (s.release > 0) {
                    s.release = Math.max(0, s.release - Slot.RELEASE_PER_TICK);
                }
                s.from = null;
                s.fromNormal = null;
                s.fromFrame = null;
                s.step.settle();
            } else {
                float wire = v.pending.phase(i);
                boolean moved = s.target == null || s.target.point().distanceToSqr(next.point()) > 1.0E-6;
                if (moved) {
                    Vec3 tipNow = s.tipNow(tick), normalNow = s.normalNow(tick);
                    if (s.target != null && !s.step.stepping()) {
                        v.lifts++;
                        RigSounds.lift(player, s.point(1));
                    }
                    s.silent = s.target == null && s.release <= 0 && wire >= 1;
                    Frame fromFrame = s.target != null ? s.frame : null;
                    s.fromFrame = tipNow == null ? null : fromFrame;
                    s.from = tipNow == null ? null : toLocal(s.fromFrame, tipNow, 1);
                    s.fromNormal = normalNow == null ? null : normalToLocal(s.fromFrame, normalNow, 1);
                    s.step.start(wire);
                } else {
                    planted = s.step.advance(wire) && !s.silent;
                }
                s.release = s.releaseO = 0;
            }
            s.target = next;
            s.frame = nextFrame;
            if (planted) {
                v.plants++;
                boolean own = player == Minecraft.getInstance().player;
                Vec3 at = s.point(1);
                boolean near = RigSounds.inHearing(at);
                if (near) PLANTS_NEAR[own ? 0 : 1]++;
                if (RigSounds.plant(player, at)) {
                    v.plantSounds++;
                    PLANT_SOUNDS[own ? 0 : 1]++;
                }
                RigParticles.puff(player, at, normalToWorld(s.frame, faceNormal(next.face()), 1));
            }
        }
        ownMove(player, v, out);
        RigSounds.whir(player, v);
    }

    /** Speed and height hiss from the move in the carry Ship's frame; a world move when there is none or it just changed. */
    private static void ownMove(Player player, Visual v, boolean out) {
        Vec3 pos = player.position();
        Frame ride = carryFrame(v);
        Vec3 local = ride == null ? null : ride.toLocal(pos);
        if (v.lastPos != null) {
            Vec3 d = ride != null && ride.id().equals(v.lastRide) ? ride.normalToWorld(local.subtract(v.lastLocal)) : pos.subtract(v.lastPos);
            v.speed = v.speed * 0.6 + d.length() * 0.4;
            boolean vert = out && Math.abs(d.y) > 0.04;
            if (vert) {
                if (v.vertTicks == 0 && v.stillVertTicks > 6) RigSounds.hiss(player, 0.12F, 1.3F);
                v.vertTicks++;
                v.stillVertTicks = 0;
            } else {
                v.vertTicks = 0;
                v.stillVertTicks++;
            }
        }
        v.lastPos = pos;
        v.lastLocal = local;
        v.lastRide = ride == null ? null : ride.id();
    }

    /** The Ship holding most of the drawn Grips, if at least {@link Grips#HELD} (RigClient's carry Ship rule). */
    private static Frame carryFrame(Visual v) {
        Frame best = null;
        int bestCount = Grips.HELD - 1;
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Frame f = v.slots[i].frame;
            if (f == null || v.slots[i].target == null) continue;
            int n = 0;
            for (int j = 0; j <= i; j++) if (f.equals(v.slots[j].frame)) n++;
            if (n > bestCount) {
                bestCount = n;
                best = f;
            }
        }
        return best;
    }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    public static Vec3 faceNormal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** {@code frame} null: the world (points pass through). */
    /** Guard against a Ship pose this client does not have yet: a point there sits at raw plot coords, far from the player. */
    static boolean plausible(Player player, Vec3 world) {
        return plausible(player.position(), world);
    }

    static boolean plausible(Vec3 wearer, Vec3 world) {
        if (!Double.isFinite(world.x) || !Double.isFinite(world.y) || !Double.isFinite(world.z)) return false;
        return world.distanceToSqr(wearer) <= MAX_GRIP_DISTANCE * MAX_GRIP_DISTANCE;
    }

    static Vec3 toWorld(Frame frame, Vec3 p, float pt) {
        return frame == null ? p : frame.renderPose(pt).toWorld(p);
    }

    static Vec3 toLocal(Frame frame, Vec3 p, float pt) {
        return frame == null ? p : frame.renderPose(pt).toLocal(p);
    }

    static Vec3 normalToWorld(Frame frame, Vec3 n, float pt) {
        return frame == null ? n : frame.renderPose(pt).normalToWorld(n);
    }

    static Vec3 normalToLocal(Frame frame, Vec3 n, float pt) {
        return frame == null ? n : frame.renderPose(pt).normalToLocal(n);
    }
}
