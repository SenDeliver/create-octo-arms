package dev.tentaworks.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Keeping an Actuator out of blocks, free of rendering: a block face is worked from its open side (the contact sits off the face
 * along its normal, a tool's head goes into the face so its handle and the gripper stay outside), and a chain whose bend would
 * pass through a block turns its bend about the line from root to wrist to the side with the fewest points inside. {@code solid}
 * tells whether a world point is inside a block.
 */
public final class ActuatorClearance {
    /** How far a contact stays off the face it works on, blocks. */
    public static final double FACE_CLEAR = 0.04;
    /** How far the far end of a carried item at rest stays off a face, blocks: its drawn end and the gripper round it are not exact. */
    public static final double ITEM_CLEAR = 0.1;
    /** A tool at its impact, and a thrust, goes into the face at least this steeply (sine of the angle to the face). */
    public static final double MIN_INTO = 0.45;
    /** Turns of the bend tried, in order, radians: the usual one first, then either way round, the opposite last. */
    static final double[] BENDS = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2, 3 * Math.PI / 4, -3 * Math.PI / 4, Math.PI};
    /** Points tested along each segment of a chain. */
    static final int PER_SEGMENT = 3;

    private ActuatorClearance() {
    }

    /** {@code axis} (unit) turned toward {@code -n} just enough to go into a face of normal {@code n} by at least {@code minSin}. */
    public static Vec3 into(Vec3 axis, Vec3 n, double minSin) {
        Vec3 a = axis.normalize();
        double an = a.dot(n);
        if (an <= -minSin) return a;
        Vec3 t = a.subtract(n.scale(an));
        if (t.lengthSqr() < 1.0E-10) t = across(n);
        return t.normalize().scale(Math.sqrt(1 - minSin * minSin)).subtract(n.scale(minSin));
    }

    /**
     * A swing plane for a strike on a face of normal {@code n}: the impact axis goes into the face ({@link #into}), and the way the
     * tool rears back never points into it, so neither the handle at the impact nor the head on the wind-up enter the block.
     */
    public static ActuatorPose.Swing onFace(ActuatorPose.Swing plane, Vec3 n, double minSin) {
        Vec3 i = into(plane.impact(), n, minSin);
        Vec3 b = plane.back().subtract(i.scale(plane.back().dot(i)));
        double bn = b.dot(n);
        if (bn < 0) {
            Vec3 p = n.subtract(i.scale(n.dot(i)));
            double pn = p.dot(n);
            if (pn > 1.0E-6) b = b.add(p.scale(-bn / pn));
        }
        if (b.lengthSqr() < 1.0E-8) b = i.cross(n).lengthSqr() > 1.0E-8 ? i.cross(n) : across(i);
        return ActuatorPose.Swing.of(i, b);
    }

    /**
     * An item's unit {@code axis} from a grip {@code gap} off a face of normal {@code n}, turned toward the face's plane just enough
     * that its far end, {@code length} along it, stays {@link #ITEM_CLEAR} off the face; the same axis when it already does.
     */
    public static Vec3 tilt(Vec3 axis, Vec3 n, double gap, double length) {
        if (length < 1.0E-6) return axis;
        double max = Mth.clamp((gap - ITEM_CLEAR) / length, -1, 1);
        return axis.dot(n) >= -max ? axis : into(axis, n.scale(-1), -max);
    }

    /** A contact on a face: the hit point moved by {@code nudge} along the face only, then {@link #FACE_CLEAR} off it. */
    public static Vec3 onFace(Vec3 hit, Vec3 nudge, Vec3 n) {
        return hit.add(nudge.subtract(n.scale(nudge.dot(n)))).add(n.scale(FACE_CLEAR));
    }

    /** {@code v} turned by {@code angle} radians about the unit {@code axis}. */
    public static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        if (angle == 0) return v;
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    /** Points of a chain inside a block: {@link #PER_SEGMENT} along each segment, the root left out. */
    public static int inside(ActuatorPose.Chain chain, Predicate<Vec3> solid) {
        Vec3[] j = chain.joints();
        int n = 0;
        for (int i = 0; i + 1 < j.length; i++)
            for (int t = 1; t <= PER_SEGMENT; t++) if (solid.test(j[i].lerp(j[i + 1], t / (double) PER_SEGMENT))) n++;
        return n;
    }

    /** The chain from {@code s} to {@code w} with its bend turned by {@code turn} radians about the line from root to wrist. */
    public static ActuatorPose.Chain chain(Vec3 s, Vec3 w, Vec3 bulge, Vec3 fallback, double segment, double turn) {
        Vec3 d = w.subtract(s);
        if (turn == 0 || d.lengthSqr() < 1.0E-10) return ActuatorPose.chain(s, w, bulge, fallback, segment);
        Vec3 u = d.normalize();
        return ActuatorPose.chain(s, w, rotate(bulge, u, turn), rotate(fallback, u, turn), segment);
    }

    /**
     * The turn of the bend that keeps a chain out of blocks: none while the usual bend is clear, else {@code current} while that
     * is clear, else the first of {@link #BENDS} that is, else the one with the fewest points inside (the usual one on a tie).
     */
    public static double bend(Vec3 s, Vec3 w, Vec3 bulge, Vec3 fallback, double segment, double current, Predicate<Vec3> solid) {
        int best = inside(chain(s, w, bulge, fallback, segment, 0), solid);
        if (best == 0) return 0;
        if (current != 0 && inside(chain(s, w, bulge, fallback, segment, current), solid) == 0) return current;
        double pick = 0;
        for (int k = 1; k < BENDS.length; k++) {
            int n = inside(chain(s, w, bulge, fallback, segment, BENDS[k]), solid);
            if (n < best) {
                best = n;
                pick = BENDS[k];
                if (n == 0) break;
            }
        }
        return pick;
    }

    /**
     * A face normal {@code n} turned toward the new face's {@code face} by {@code k} of the angle between them, so the strike turns
     * as fast as its target moves; a face across from it (a ceiling after the floor) turns through the side. Null either side takes
     * {@code face}.
     */
    public static Vec3 turnNormal(Vec3 n, Vec3 face, double k) {
        if (n == null || face == null) return face;
        double c = Mth.clamp(n.dot(face), -1, 1);
        Vec3 axis = n.cross(face);
        if (axis.lengthSqr() < 1.0E-10) {
            if (c > 0) return face;
            axis = across(n);
        }
        return rotate(n, axis.normalize(), Math.acos(c) * Mth.clamp(k, 0, 1)).normalize();
    }

    /** {@code from} moved toward {@code to} by {@code k} of the way round the shorter side of the circle, radians. */
    public static double turnToward(double from, double to, double k) {
        return from + Mth.wrapDegrees(Math.toDegrees(to - from)) * Math.PI / 180 * Mth.clamp(k, 0, 1);
    }

    private static Vec3 across(Vec3 n) {
        Vec3 any = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return any.subtract(n.scale(any.dot(n))).normalize();
    }
}
