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
        Grip target;
        Frame frame;
        Vec3 from;
        Vec3 fromNormal;
        Frame fromFrame;
        float phase = 1, phaseO = 1;

        public Grip target() {
            return target;
        }

        public float phase(float pt) {
            return phaseO + (phase - phaseO) * pt;
        }

        /** The target point in world coords (Ship render pose), or null. */
        public Vec3 point(float pt) {
            return target == null ? null : toWorld(frame, target.point(), pt);
        }

        /** Tip on the arc from the last Grip (or null: from the free pose) to the target, world coords. */
        public Vec3 tip(float pt, Vec3 free) {
            if (target == null) return null;
            float ph = phase(pt);
            Vec3 a = from != null ? toWorld(fromFrame, from, pt) : free;
            Vec3 b = point(pt);
            if (ph >= 1 || a == null) return b;
            double e = smooth(ph);
            Vec3 n = normal(pt);
            double h = Math.min(1.0, 0.3 + 0.3 * a.distanceTo(b));
            return a.lerp(b, e).add(n.scale(h * Math.sin(Math.PI * e)));
        }

        /** World coords. */
        public Vec3 normal(float pt) {
            Vec3 to = normalToWorld(frame, faceNormal(target.face()), pt);
            if (fromNormal == null) return to;
            double e = smooth(phase(pt));
            Vec3 n = normalToWorld(fromFrame, fromNormal, pt).lerp(to, e);
            return n.lengthSqr() < 1.0E-4 ? to : n.normalize();
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

    static void remote(RigStatePacket packet) {
        Visual v = VISUALS.computeIfAbsent(packet.entityId(), k -> new Visual());
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
            float ph = next == null ? 1 : v.pending.phase(i);
            s.phaseO = s.phase;
            boolean moved = next != null && (s.target == null || s.target.point().distanceToSqr(next.point()) > 1.0E-6);
            if (moved) {
                if (s.target != null) {
                    s.fromFrame = s.frame;
                    s.from = toLocal(s.fromFrame, s.tip(1, null), 1);
                    s.fromNormal = normalToLocal(s.fromFrame, s.normal(1), 1);
                    if (s.phase >= 1) {
                        v.lifts++;
                        RigSounds.lift(player, s.point(1));
                    }
                } else {
                    s.from = null;
                    s.fromNormal = null;
                    s.fromFrame = null;
                }
                s.phaseO = Math.min(s.phaseO, ph);
            }
            if (next == null) s.from = null;
            boolean planted = next != null && ph >= 1 && s.phase < 1 && !moved;
            planted |= next != null && moved && ph >= 1 && s.target != null && v.deploy >= 1;
            s.target = next;
            s.frame = nextFrame;
            s.phase = ph;
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
