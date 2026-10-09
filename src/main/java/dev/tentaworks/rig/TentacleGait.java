package dev.tentaworks.rig;

import dev.tentaworks.compat.Frame;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Which Grip each Tentacle holds, and when it Steps (client side; the server only validates the Grips).
 * Slots are body-relative: 0 front left, 1 front right, 2 back left, 3 back right; each prefers a spread target in its own
 * quadrant (front ones high, back ones low, so a wall also gets four spread Grips). A planted Tentacle keeps its Grip until
 * it goes invalid or out of reach, falls behind its target along the Heading, or (Idle) a clearly better Grip exists.
 * At most two Tentacles step at once, a diagonal pair (0+3, 1+2), and a voluntary Step never leaves fewer than two planted.
 * A stepping slot already carries its new Grip (the arc is drawn only), so the server always sees every Grip.
 * Grips may lie a little past Reach (≤ {@link #overshoot}); the server allows Reach + 0.75.
 * Candidates on a Ship are picked in its plot coords; distances between Grips and to the body compare world points.
 */
public final class TentacleGait {
    public static final int SLOTS = Tentacles.SLOTS;
    private static final int[] PARTNER = {3, 2, 1, 0};
    private static final double SEPARATION_WEIGHT = 8.0;
    private static final double SEPARATION = 1.7;
    private static final double IDLE_GAIN = 0.5;
    /**
     * A held or targeted Grip is kept while within Reach + this of the body both before and after this tick's move: the server
     * checks the packet against its pre-move position and the tick against the post-move one, allowing Reach + 0.75.
     */
    private static final double KEEP_SLACK = 0.68;

    private final Grip[] grips = new Grip[SLOTS];
    private final float[] phase = {1, 1, 1, 1};
    private final int[] duration = new int[SLOTS];
    private final boolean[] planted = new boolean[SLOTS];
    private final boolean[] lifted = new boolean[SLOTS];
    private int lastPair = -1;
    private Vec3 before = Vec3.ZERO;
    private Level level;
    private final Grip[] worldOf = new Grip[SLOTS];
    private final Vec3[] world = new Vec3[SLOTS];
    private double keep;
    private int plants;
    private int steps;

    public Grip grip(int slot) {
        return grips[slot];
    }

    public Grip[] grips() {
        return grips.clone();
    }

    public float phase(int slot) {
        return phase[slot];
    }

    public boolean plantedNow(int slot) {
        return planted[slot];
    }

    public boolean liftedNow(int slot) {
        return lifted[slot];
    }

    public int plants() {
        return plants;
    }

    public int steps() {
        return steps;
    }

    public int stepping() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (grips[i] != null && phase[i] < 1) n++;
        return n;
    }

    public Tentacles wire() {
        byte[] b = new byte[SLOTS];
        for (int i = 0; i < SLOTS; i++) b[i] = Tentacles.phaseByte(phase[i]);
        return new Tentacles(grips.clone(), b);
    }

    public void clear() {
        for (int i = 0; i < SLOTS; i++) {
            grips[i] = null;
            phase[i] = 1;
            planted[i] = lifted[i] = false;
        }
    }

    /** How far past Reach a Grip may lie: what is left of the server's 0.75 slack after one tick of top-speed motion. */
    public static double overshoot(double maxMotionPerTick) {
        return Mth.clamp(0.68 - maxMotionPerTick, 0.0, 0.3);
    }

    /**
     * One tick. {@code body} is the body centre after this tick's move, {@code motion} the move (collided), {@code yaw} the body yaw.
     * Faces must cover {@code reach + overshoot} around the body.
     */
    public void tick(Level level, List<Grips.Face> faces, Vec3 body, float yaw, Vec3 motion, double reach, double overshoot) {
        double limit = reach + overshoot;
        double speed = motion.length();
        keep = reach + KEEP_SLACK;
        before = body.subtract(motion);
        this.level = level;
        Arrays.fill(worldOf, null);
        int stepTicks = speed < 0.02 ? 7 : Mth.clamp(Math.round((float) (1.0 / speed)), 2, 7);
        Vec3 ahead = body.add(motion.scale(stepTicks));
        for (int i = 0; i < SLOTS; i++) {
            planted[i] = lifted[i] = false;
            if (grips[i] != null && phase[i] < 1) {
                phase[i] = Math.min(1, phase[i] + 1.0F / Math.max(1, duration[i]));
                if (phase[i] >= 1) {
                    planted[i] = true;
                    plants++;
                }
            }
        }
        Vec3[] target = targets(body, yaw, reach);
        boolean any = false;
        for (Grip g : grips) any |= g != null;
        if (!any) {
            for (int i = 0; i < SLOTS; i++) {
                grips[i] = best(faces, body, body, target[i], limit, i);
                phase[i] = 1;
            }
            return;
        }

        boolean[] ok = new boolean[SLOTS];
        int plantedOk = 0;
        for (int i = 0; i < SLOTS; i++) {
            ok[i] = grips[i] != null && Grips.valid(level, grips[i], body, keep) && world(i).distanceTo(before) <= keep;
            if (ok[i] && phase[i] >= 1) plantedOk++;
            if (grips[i] != null && !ok[i] && phase[i] < 1) {
                grips[i] = best(faces, body, ahead, target[i], limit, i);
                if (grips[i] == null) phase[i] = 1;
            }
        }

        Vec3 dir = speed < 0.02 ? null : motion.scale(1 / speed);
        double stride = 0.35 * reach;
        double[] need = new double[SLOTS];
        Grip[] next = new Grip[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            need[i] = Double.NEGATIVE_INFINITY;
            if (phase[i] < 1 || planted[i]) continue;
            if (grips[i] == null || !ok[i]) {
                next[i] = best(faces, body, ahead, target[i], limit, i);
                if (next[i] != null || grips[i] != null) need[i] = 1000;
                continue;
            }
            Vec3 aim = dir == null ? target[i] : target[i].add(dir.scale(stride));
            if (dir != null) {
                double behind = -world(i).subtract(target[i]).dot(dir) - stride;
                if (behind <= 0) continue;
                Grip cand = best(faces, body, ahead, aim, limit, i);
                if (cand == null || cand.world(level).subtract(world(i)).dot(dir) < 0.5) continue;
                next[i] = cand;
                need[i] = behind;
            } else {
                Grip cand = best(faces, body, ahead, aim, limit, i);
                if (cand == null) continue;
                double gain = score(world(i), aim, i) - score(cand.world(level), aim, i);
                if (gain < IDLE_GAIN) continue;
                next[i] = cand;
                need[i] = gain;
            }
        }

        int active = stepping();
        for (int round = 0; round < 2 && active < 2; round++) {
            int pick = -1;
            double bestNeed = 0;
            for (int i = 0; i < SLOTS; i++) {
                if (need[i] == Double.NEGATIVE_INFINITY) continue;
                boolean forced = need[i] >= 1000;
                if (!forced) {
                    if (plantedOk - 1 < Grips.HELD) continue;
                    int other = otherStepper(i);
                    if (other >= 0 && other != PARTNER[i]) continue;
                }
                double n = need[i] + (pairOf(i) != lastPair ? 0.25 : 0);
                if (pick < 0 || n > bestNeed) {
                    pick = i;
                    bestNeed = n;
                }
            }
            if (pick < 0) break;
            boolean wasPlanted = ok[pick];
            grips[pick] = next[pick];
            need[pick] = Double.NEGATIVE_INFINITY;
            if (grips[pick] == null) {
                phase[pick] = 1;
                continue;
            }
            phase[pick] = 0;
            duration[pick] = stepTicks;
            lifted[pick] = true;
            steps++;
            lastPair = pairOf(pick);
            if (wasPlanted) plantedOk--;
            active++;
        }
    }

    /** World point of slot {@code i}'s Grip, resolved once per tick per Grip. */
    private Vec3 world(int i) {
        if (worldOf[i] != grips[i]) {
            worldOf[i] = grips[i];
            world[i] = grips[i].world(level);
        }
        return world[i];
    }

    private int otherStepper(int self) {
        for (int i = 0; i < SLOTS; i++) if (i != self && grips[i] != null && phase[i] < 1) return i;
        return -1;
    }

    private static int pairOf(int slot) {
        return slot == 0 || slot == 3 ? 0 : 1;
    }

    /** Spread target of each slot: out on its body diagonal, front slots raised and back slots lowered. */
    public static Vec3[] targets(Vec3 body, float yaw, double reach) {
        float r = yaw * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
        Vec3 left = new Vec3(Mth.cos(r), 0, Mth.sin(r));
        double rho = Mth.clamp(0.5 * reach, 1.1, 2.8);
        double rise = 0.35 * reach;
        Vec3[] out = new Vec3[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            int f = i < 2 ? 1 : -1;
            int l = (i & 1) == 0 ? 1 : -1;
            out[i] = body.add(fwd.scale(f).add(left.scale(l)).normalize().scale(rho)).add(0, f * rise, 0);
        }
        return out;
    }

    /**
     * Face point nearest {@code aim} (spread from the other Grips) within {@code limit} of the body now and where it will be at
     * plant time, and within the keep distance of the body before this tick's move (what the server checks the packet against).
     * All world points; each face is searched in its own frame.
     */
    private Grip best(List<Grips.Face> faces, Vec3 worldBody, Vec3 worldAhead, Vec3 worldAim, double limit, int slot) {
        double k2 = keep * keep;
        double l2 = limit * limit;
        Grip out = null;
        double bestScore = Double.MAX_VALUE;
        Frame frame = null;
        Vec3 body = worldBody, ahead = worldAhead, aim = worldAim, before = this.before;
        for (Grips.Face f : faces) {
            if (f.frame() != frame) {
                frame = f.frame();
                body = frame == null ? worldBody : frame.toLocal(worldBody);
                ahead = frame == null ? worldAhead : frame.toLocal(worldAhead);
                aim = frame == null ? worldAim : frame.toLocal(worldAim);
                before = frame == null ? this.before : frame.toLocal(this.before);
            }
            AABB r = f.rect();
            Direction d = f.dir();
            double plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? r.max(d.getAxis()) : r.min(d.getAxis());
            if ((body.get(d.getAxis()) - plane) * d.getAxisDirection().getStep() <= 0.05) continue;
            Vec3 foot = clampTo(body, r);
            if (foot.distanceToSqr(body) > l2) continue;
            Vec3 p = clampTo(aim, r);
            if (p.distanceToSqr(body) > l2) p = pullIn(foot, p, body, limit);
            if (p.distanceToSqr(ahead) > l2) {
                Vec3 footAhead = clampTo(ahead, r);
                if (footAhead.distanceToSqr(ahead) > l2) continue;
                p = pullIn(footAhead, p, ahead, limit);
                if (p.distanceToSqr(body) > l2) continue;
            }
            if (p.distanceToSqr(before) > k2) continue;
            double s = score(frame == null ? p : frame.toWorld(p), worldAim, slot);
            if (s < bestScore) {
                bestScore = s;
                out = new Grip(p, f.block(), d);
            }
        }
        return out;
    }

    /** {@code p} and {@code aim} in world coords. */
    private double score(Vec3 p, Vec3 aim, int slot) {
        double s = p.distanceToSqr(aim);
        for (int j = 0; j < SLOTS; j++) {
            if (j == slot || grips[j] == null) continue;
            double gap = Math.max(0, SEPARATION - p.distanceTo(world(j)));
            s += SEPARATION_WEIGHT * gap * gap;
        }
        return s;
    }

    /** Point on segment foot→p at distance {@code limit} from the body (foot is within it, p is not). */
    private static Vec3 pullIn(Vec3 foot, Vec3 p, Vec3 body, double limit) {
        Vec3 d = p.subtract(foot);
        Vec3 o = foot.subtract(body);
        double a = d.lengthSqr(), b = 2 * o.dot(d), c = o.lengthSqr() - limit * limit;
        double disc = b * b - 4 * a * c;
        if (a < 1.0E-9 || disc < 0) return foot;
        double t = Mth.clamp((-b + Math.sqrt(disc)) / (2 * a), 0, 1) * 0.999;
        return foot.add(d.scale(t));
    }

    private static Vec3 clampTo(Vec3 p, AABB r) {
        return new Vec3(Mth.clamp(p.x, r.minX, r.maxX), Mth.clamp(p.y, r.minY, r.maxY), Mth.clamp(p.z, r.minZ, r.maxZ));
    }
}
