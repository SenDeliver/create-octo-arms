package dev.tentaworks.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pose math of the Actuators, free of rendering. An Actuator is a chain of {@link #SEGMENTS} rigid segments of one length, each
 * turned from the last by the same angle, so the chain curls evenly and never stretches or skews; it reaches farther only by
 * sliding every segment's rod out of its sleeve (telescoping, as the Tentacles do), up to the Rig's Reach. Rest Pose points are in
 * tank space (blocks: +X the wearer's right, +Y up, -Z toward the wearer's front, x = 0.5 the body's centre line); a standing
 * wearer's height above the feet is {@code (TANK_FLOOR + y) * MODEL_SCALE}. First-person points are in camera space (+X right,
 * +Y up, +Z ahead).
 */
public final class ActuatorPose {
    public static final int LEFT = -1, RIGHT = 1;
    /** Player model scale, and the tank-space height of the feet below tank y = 0. */
    public static final double MODEL_SCALE = 0.9375, TANK_FLOOR = 0.876;
    /** Half the player's drawn width, arms included, and the top of the head, blocks. */
    public static final double BODY_HALF_WIDTH = 0.47, HEAD_TOP = 1.8;
    /** Actuator shoulder hubs on top of the tank (tank space, blocks). */
    public static final double SHOULDER_X = 0.25, SHOULDER_Y = 0.84, SHOULDER_Z = 0.47;
    /** Rigid segments per Actuator, and the shortest a segment gets (blocks). */
    public static final int SEGMENTS = 3;
    public static final double MIN_SEGMENT = 0.14;
    /** Rest Pose wrist from the shoulder (tank space): out, up, ahead; how straight the chain is (wrist distance / chain length). */
    public static final double REST_OUT = 0.5, REST_UP = 0.42, REST_AHEAD = 0.22, REST_STRAIGHT = 0.7;
    /** Ticks of one slow breath (both Actuators together) and of one finger flex cycle (each its own phase). */
    public static final float BREATH_TICKS = 70, GESTURE_TICKS = 170, GESTURE_LENGTH = 24;
    /** First person: distance of an Actuator's root from the eye, how far below it, and the angle past the frame's edge. */
    public static final double ENTRY_DISTANCE = 0.8, ENTRY_DROP = 0.32, ENTRY_MARGIN_DEGREES = 14;
    public static final float MIN_OPACITY = 0.1F;
    /** Ticks for a reach to go out and to come back, the grab wind-up, the throw snap and return, and the Fitting flex. */
    public static final float REACH_TICKS = 5, RETURN_TICKS = 7, WIND_UP_TICKS = 5, SNAP_TICKS = 2, FOLLOW_TICKS = 16, FLEX_TICKS = 20;

    private ActuatorPose() {
    }

    public static double shoulderX(int side) {
        return 0.5 + side * SHOULDER_X;
    }

    public static Vec3 shoulder(int side) {
        return new Vec3(shoulderX(side), SHOULDER_Y, SHOULDER_Z);
    }

    /** Slow breathing, -1..1, both Actuators in step. */
    public static double breathe(float time) {
        return Math.sin(time / BREATH_TICKS * Math.PI * 2);
    }

    /** An occasional small finger flex, 0 most of the time, a smooth 0..1..0 bump once per cycle, each side its own phase. */
    public static double gesture(float time, int side) {
        float cycle = time + (side > 0 ? GESTURE_TICKS * 0.55F : 0);
        float in = cycle - Mth.floor(cycle / GESTURE_TICKS) * GESTURE_TICKS;
        if (in >= GESTURE_LENGTH) return 0;
        double s = Math.sin(in / GESTURE_LENGTH * Math.PI);
        return s * s;
    }

    /** Rest Pose wrist (tank space): curled above the shoulder, rising and falling a little with the breath. */
    public static Vec3 restWrist(int side, float time) {
        double b = breathe(time);
        return shoulder(side).add(side * REST_OUT, REST_UP + 0.025 * b, -REST_AHEAD);
    }

    /** Which way the Rest Pose chain bulges (tank space): up, out and back, so the gripper hangs forward and down. */
    public static Vec3 restBulge(int side) {
        return new Vec3(side * 0.45, 1, 0.35);
    }

    /** Rest Pose straightness: the curl tightens a little on the in-breath. */
    public static double restStraight(float time) {
        return REST_STRAIGHT - 0.03 * breathe(time);
    }

    /** Gripper opening at rest: closed, flexing a little now and then. */
    public static float restOpen(int side, float time) {
        return (float) (0.22 * gesture(time, side));
    }

    /** Height above a standing wearer's feet of a tank-space point. */
    public static double heightAboveFeet(Vec3 tank) {
        return (TANK_FLOOR + tank.y) * MODEL_SCALE;
    }

    /** Sideways offset from the body's centre line of a tank-space point, blocks (negative = the wearer's left). */
    public static double sideways(Vec3 tank) {
        return (tank.x - 0.5) * MODEL_SCALE;
    }

    /**
     * The joints of an Actuator from root {@code s} to its wrist, {@link #SEGMENTS} + 1 points. Every segment is {@code segment}
     * long; consecutive segments turn by one angle in the plane of {@code s → w} and {@code bulge}, so the chain arcs toward
     * {@code bulge} and ends on {@code w}. A wrist past the chain's reach is pulled in toward it (softly, so the arm never snaps
     * straight): the chain then points at it, almost straight. {@code fallback} picks the plane where {@code bulge} lies along the
     * chain, and the direction when {@code w} is on the root.
     */
    public static Chain chain(Vec3 s, Vec3 w, Vec3 bulge, Vec3 fallback, double segment) {
        Vec3 d = w.subtract(s);
        double dist = d.length();
        Vec3 u = dist < 1.0E-6 ? unit(fallback, new Vec3(0, 1, 0)) : d.scale(1 / dist);
        double reach = softReach(dist, SEGMENTS * segment);
        Vec3 v = across(bulge, fallback, u);
        double phi = turn(reach / segment, SEGMENTS);
        Vec3[] joints = new Vec3[SEGMENTS + 1];
        joints[0] = s;
        for (int j = 0; j < SEGMENTS; j++) {
            double a = ((SEGMENTS - 1) / 2.0 - j) * phi;
            joints[j + 1] = joints[j].add(u.scale(Math.cos(a) * segment)).add(v.scale(Math.sin(a) * segment));
        }
        return new Chain(joints, u.cross(v).normalize());
    }

    /**
     * Segment length for a wrist {@code dist} from the root at straightness {@code straight} (wrist distance / chain length):
     * at least {@link #MIN_SEGMENT}, at most {@code maxSegment} (the Rig's Reach over the segments).
     */
    public static double segment(double dist, double straight, double maxSegment) {
        double s = Mth.clamp(straight, 0.3, 0.99);
        return Mth.clamp(dist / (SEGMENTS * s), MIN_SEGMENT, Math.max(MIN_SEGMENT, maxSegment));
    }

    /** {@code dist} kept within a chain of length {@code length}: unchanged up to 90%, then easing toward 98.5%. */
    static double softReach(double dist, double length) {
        double knee = 0.9 * length, room = 0.085 * length;
        if (dist <= knee) return dist;
        return knee + room * (1 - Math.exp(-(dist - knee) / room));
    }

    /** Turn between segments so {@code n} unit segments end {@code ratio} from the start: solves sin(nφ/2) / sin(φ/2) = ratio. */
    static double turn(double ratio, int n) {
        if (ratio >= n) return 0;
        double lo = 0, hi = 2 * Math.PI / n * 0.92;
        if (ratio <= span(hi, n)) return hi;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (span(mid, n) > ratio) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    private static double span(double phi, int n) {
        return phi < 1.0E-9 ? n : Math.sin(n * phi / 2) / Math.sin(phi / 2);
    }

    /** Unit vector across {@code u} toward {@code bulge}, turning to {@code fallback} smoothly as {@code bulge} lines up with {@code u}. */
    static Vec3 across(Vec3 bulge, Vec3 fallback, Vec3 u) {
        Vec3 p = off(bulge, u), q = off(fallback, u);
        double pl = p.length(), bl = bulge.length();
        double k = bl < 1.0E-9 ? 1 : smooth(1 - pl / (0.3 * bl));
        Vec3 v = (pl > 1.0E-9 ? p.scale(1 / pl) : Vec3.ZERO).scale(1 - k).add(unit(q, Vec3.ZERO).scale(k));
        if (v.lengthSqr() > 1.0E-8) return v.normalize();
        Vec3 any = Math.abs(u.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return off(any, u).normalize();
    }

    private static Vec3 off(Vec3 a, Vec3 u) {
        return a.subtract(u.scale(a.dot(u)));
    }

    private static Vec3 unit(Vec3 a, Vec3 or) {
        return a.lengthSqr() < 1.0E-12 ? or : a.normalize();
    }

    /** A solved Actuator: its joints, root to wrist, and the hinge axis all its joints bend about. */
    public record Chain(Vec3[] joints, Vec3 hinge) {
        public Vec3 wrist() {
            return joints[joints.length - 1];
        }

        /** Direction of the last segment: the way the gripper points. */
        public Vec3 wristDir() {
            return joints[joints.length - 1].subtract(joints[joints.length - 2]).normalize();
        }

        public double segment() {
            return joints[1].distanceTo(joints[0]);
        }
    }

    /**
     * First person: where an Actuator enters the view (camera space): beside the eye on its own side, just below it, past the
     * frame's edge for a vertical field of view {@code fovDegrees} and aspect {@code aspect}, so an arm arcs in from the side and
     * never from the centre.
     */
    public static Vec3 entry(int side, double fovDegrees, double aspect) {
        double half = Math.atan(Math.tan(Math.toRadians(Mth.clamp(fovDegrees, 1, 179)) / 2) * Math.max(0.1, aspect));
        double a = Math.min(half + Math.toRadians(ENTRY_MARGIN_DEGREES), Math.toRadians(150));
        return new Vec3(side * ENTRY_DISTANCE * Math.sin(a), -ENTRY_DROP, ENTRY_DISTANCE * Math.cos(a));
    }

    /** Whether a camera-space point is inside the view frustum (near plane ignored). */
    public static boolean inFrame(Vec3 p, double fovDegrees, double aspect) {
        if (p.z <= 0) return false;
        double tv = Math.tan(Math.toRadians(fovDegrees) / 2);
        return Math.abs(p.y) <= p.z * tv && Math.abs(p.x) <= p.z * tv * aspect;
    }

    /** Configured first-person Limb opacity, clamped to {@link #MIN_OPACITY}..1 (never invisible). */
    public static float opacity(double configured) {
        if (!Double.isFinite(configured)) return 1;
        return (float) Mth.clamp(configured, MIN_OPACITY, 1);
    }

    public static int alpha(double configured) {
        return Math.round(opacity(configured) * 255);
    }

    public static double smooth(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Hydraulic ease out: moves at once, settles without overshoot. */
    public static double easeOut(double k) {
        k = 1 - Mth.clamp(k, 0, 1);
        return 1 - k * k * k;
    }

    /** Hydraulic ease in: slow start, firm stop. */
    public static double easeIn(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * k;
    }

    /**
     * A 0..1 level that set off from {@code from} toward on or off {@code age} ticks ago: out with {@link #easeOut} over
     * {@link #REACH_TICKS}, back with {@link #easeIn} over {@link #RETURN_TICKS} (shorter for a shorter way). Never jumps when
     * the goal flips mid-way: a flip starts a new run from the level at that moment.
     */
    public static double level(double from, boolean on, float age) {
        if (on) return from + (1 - from) * easeOut(age / (REACH_TICKS * (float) Math.max(0.2, 1 - from)));
        return from * (1 - easeIn(age / (RETURN_TICKS * (float) Math.max(0.2, from))));
    }

    /** Grab start: how far the arm is cocked back (0..1) {@code age} ticks in; it pulls back, then lunges to the target. */
    public static double windUp(float age) {
        if (age <= 0 || age >= WIND_UP_TICKS) return 0;
        float peak = WIND_UP_TICKS * 0.4F;
        return age < peak ? smooth(age / peak) : 1 - smooth((age - peak) / (WIND_UP_TICKS - peak));
    }

    /** Throw: how far the arm follows through along the throw (0..1) {@code age} ticks in: a quick snap out, a slow return. */
    public static double followThrough(float age) {
        if (age <= 0 || age >= FOLLOW_TICKS) return 0;
        return age < SNAP_TICKS ? easeOut(age / SNAP_TICKS) : 1 - smooth((age - SNAP_TICKS) / (FOLLOW_TICKS - SNAP_TICKS));
    }

    /** Fitting flex: folded (1) unfolding to rest (0) over the first 40% of the flex. */
    public static double folded(float age) {
        if (age < 0 || age >= FLEX_TICKS) return 0;
        return 1 - smooth(age / (FLEX_TICKS * 0.4F));
    }

    /** Fitting flex: the fingers open and close twice once unfolded, 0..1. */
    public static double flexOpen(float age) {
        float from = FLEX_TICKS * 0.4F;
        if (age < from || age >= FLEX_TICKS) return 0;
        double s = Math.sin((age - from) / (FLEX_TICKS - from) * Math.PI * 2);
        return s * s;
    }

    /** Mining hammer: how far the tip pulls back along the arm for a swing progress 0..1, blocks. */
    public static double hammer(float swing) {
        return 0.22 * Math.sin(Mth.clamp(swing, 0, 1) * Math.PI);
    }

    /**
     * Carried item strike for a swing progress 0..1: how far the gripper goes ahead (x, blocks), lifts (y, blocks, negative
     * dips) and pitches down (z, radians). It lifts, chops forward and down, and comes back.
     */
    public static Vec3 strike(float swing) {
        double s = Mth.clamp(swing, 0, 1);
        return new Vec3(0.32 * Math.sin(Math.PI * s), 0.12 * Math.sin(2 * Math.PI * s), 0.7 * Math.sin(Math.PI * s));
    }

    /** Carried block push for a place, blocks ahead for a swing progress 0..1. */
    public static double push(float swing) {
        return 0.18 * Math.sin(Math.PI * Mth.clamp(swing, 0, 1));
    }
}
