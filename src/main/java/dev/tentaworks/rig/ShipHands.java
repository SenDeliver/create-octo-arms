package dev.tentaworks.rig;

import dev.tentaworks.compat.Pose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * The two Actuator tips on a held Ship (CONTEXT: Ship Grab, Turn by hand), server side. Each tip holds its own point on the hull
 * (the Ship's plot space) and turns with it; the hold and the Turn pivot on the midpoint of the two ({@link #pivot}). A tip
 * whose stroke is spent ({@link #over}: turned more than {@value #STROKE}° since it took hold, its surface facing more than
 * {@value #FACING}° away from its shoulder, or the tip more than {@value #STRETCH} farther from the shoulder than it was, or nearer
 * than {@value #NEAREST}) lets go and reaches for the first hull surface on the line from its shoulder toward the hull (a little to
 * its own side of the middle, else the middle), for
 * {@value #REACH_TICKS} ticks, then takes hold there; the other waits until it holds again, so never both are off. A new point
 * must sit well inside the limits and away from the old one, else the tip keeps its point and rests {@value #REST_TICKS} ticks.
 * The pivot moves only when a tip takes hold.
 */
public final class ShipHands {
    public static final double STROKE = 45, FACING = 60, STRETCH = 1.0, NEAREST = 0.35;
    /** Degrees a new point's surface must face its shoulder inside {@value #FACING}. */
    public static final double MARGIN = 10;
    /** Nearer than this to the old point is no new point. */
    public static final double MOVED = 0.25;
    public static final int REACH_TICKS = 6, REST_TICKS = 10;
    /** Body side of each tip: index 0 the body-left Actuator, 1 the body-right one. */
    public static final int LEFT = 0, RIGHT = 1;

    /** A hull surface point and its outward normal, both in the Ship's plot space. */
    public record Hit(Vec3 at, Vec3 normal) {
    }

    /** The first hull surface on the plot-space segment {@code from} → {@code to}, or null. */
    @FunctionalInterface
    public interface Ray {
        @Nullable Hit cast(Vec3 from, Vec3 to);
    }

    /** What a tick changed: a tip let go (the clients are told), a tip took hold (the pivot moved, and they are told). */
    public enum Change {
        NONE, LET_GO, TOOK_HOLD
    }

    /** One tip: where it holds or reaches to, the surface normal there, where it reached from; {@code reach} ticks into a reach, −1 holding. */
    public static final class Tip {
        Vec3 at, normal, from;
        int reach = -1, rest = REST_TICKS, regrips;
        double dist = Double.NaN;
        final Quaterniond taken = new Quaterniond();

        Tip(Vec3 at, Vec3 normal) {
            this.at = at;
            this.normal = normal;
            this.from = at;
        }

        public Vec3 at() {
            return at;
        }

        public Vec3 from() {
            return from;
        }

        public int reach() {
            return reach;
        }

        public boolean holding() {
            return reach < 0;
        }

        public int regrips() {
            return regrips;
        }
    }

    private final Tip[] tips;
    private Vec3 pivot;

    /** Both tips holding on {@code left} and {@code right} (plot space) of a face with outward {@code normal}, taken at Turn {@code user}. */
    public ShipHands(Vec3 left, Vec3 right, Vec3 normal, Quaterniondc user) {
        tips = new Tip[] {new Tip(left, normal), new Tip(right, normal)};
        for (Tip t : tips) t.taken.set(user);
        pivot = left.add(right).scale(0.5);
    }

    /** The camera side ({@code -1} left, {@code +1} right) of tip {@code i}. */
    public static int side(int i) {
        return i == LEFT ? -1 : 1;
    }

    public Tip tip(int i) {
        return tips[i];
    }

    /** The hold's pivot: the midpoint of the two tips when the last one took hold (plot space). */
    public Vec3 pivot() {
        return pivot;
    }

    /** A tip is reaching: the Turn goes at half rate. */
    public boolean reaching() {
        return !tips[0].holding() || !tips[1].holding();
    }

    /** Degrees of Turn between the Turn {@code taken} when a tip took hold and {@code now}: in the wearer's view, as the Turn is. */
    public static double strokeAngle(Quaterniondc taken, Quaterniondc now) {
        Quaterniond d = new Quaterniond(now).mul(new Quaterniond(taken).conjugate());
        return Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(d.w))));
    }

    /** Degrees between a world surface {@code normal} at {@code at} and the way from there to {@code shoulder}. */
    public static double facing(Vec3 normal, Vec3 at, Vec3 shoulder) {
        Vec3 to = shoulder.subtract(at);
        double l = to.length() * normal.length();
        return l < 1.0E-9 ? 0 : Math.toDegrees(Math.acos(Mth.clamp(to.dot(normal) / l, -1, 1)));
    }

    /**
     * How far past its stroke a holding tip is, as the largest share of a limit (above 1: over): Turn since it took hold of
     * {@value #STROKE}, its surface away from the shoulder of {@value #FACING}, its distance from the shoulder of what it was +
     * {@value #STRETCH}; nearer than {@value #NEAREST} counts 2.
     */
    public static double over(double angle, Vec3 normal, Vec3 at, Vec3 shoulder, double taken) {
        double d = at.distanceTo(shoulder);
        if (d < NEAREST) return 2;
        return Math.max(angle / STROKE, Math.max(facing(normal, at, shoulder) / FACING, Double.isNaN(taken) ? 0 : d / (taken + STRETCH)));
    }

    /** A new point is worth a reach: away from the old one, its surface facing the shoulder well inside {@value #FACING}, within {@code reach}. */
    public static boolean worth(Hit hit, Pose pose, Vec3 old, Vec3 shoulder, double reach) {
        Vec3 at = pose.toWorld(hit.at());
        double d = at.distanceTo(shoulder);
        return hit.at().distanceTo(old) >= MOVED && d >= NEAREST && d <= reach && facing(pose.normalToWorld(hit.normal()), at, shoulder) <= FACING - MARGIN;
    }

    /** Where tip {@code i} reaches for: the line from its shoulder to the hull {@code centre}, {@link Hold#GRIP_SPREAD} toward its side along {@code right} (world). */
    public static Vec3 toward(Vec3 centre, Vec3 right, int i) {
        return centre.add(right.scale(side(i) * Hold.GRIP_SPREAD));
    }

    /**
     * One server tick, the Ship at {@code pose}, the wearer's Turn {@code user}, the tips' {@code shoulders} (world, by
     * {@link #LEFT} and {@link #RIGHT}), the hull {@code centre} and the wearer's horizontal {@code right} (world); {@code ray}
     * finds the hull surface in plot space, no farther than {@code reach} from the shoulder.
     */
    public Change tick(Pose pose, Quaterniondc user, Vec3[] shoulders, Vec3 centre, Vec3 right, Ray ray, double reach) {
        for (int i = 0; i < 2; i++) {
            Tip t = tips[i];
            if (t.holding()) {
                if (Double.isNaN(t.dist)) t.dist = pose.toWorld(t.at).distanceTo(shoulders[i]);
                if (t.rest > 0) t.rest--;
                continue;
            }
            if (++t.reach < REACH_TICKS) continue;
            t.reach = -1;
            t.taken.set(user);
            t.dist = pose.toWorld(t.at).distanceTo(shoulders[i]);
            t.rest = REST_TICKS;
            pivot = tips[0].at.add(tips[1].at).scale(0.5);
            return Change.TOOK_HOLD;
        }
        if (reaching()) return Change.NONE;
        int pick = -1;
        double most = 1;
        for (int i = 0; i < 2; i++) {
            Tip t = tips[i];
            if (t.rest > 0) continue;
            double o = over(strokeAngle(t.taken, user), pose.normalToWorld(t.normal), pose.toWorld(t.at), shoulders[i], t.dist);
            if (o > most) {
                most = o;
                pick = i;
            }
        }
        if (pick < 0) return Change.NONE;
        Tip t = tips[pick];
        Vec3 s = shoulders[pick];
        Hit hit = null;
        for (Vec3 to : new Vec3[] {toward(centre, right, pick), centre}) {
            Vec3 aim = to.subtract(s);
            Hit h = aim.lengthSqr() < 1.0E-9 ? null : ray.cast(pose.toLocal(s), pose.toLocal(s.add(aim.normalize().scale(reach))));
            if (h != null && worth(h, pose, t.at, s, reach)) {
                hit = h;
                break;
            }
        }
        if (hit == null) {
            t.rest = REST_TICKS;
            return Change.NONE;
        }
        t.from = t.at;
        t.at = hit.at();
        t.normal = hit.normal();
        t.reach = 0;
        t.regrips++;
        return Change.LET_GO;
    }
}
