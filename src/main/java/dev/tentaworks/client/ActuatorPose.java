package dev.tentaworks.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pose math of the Actuators, free of rendering. Rest Pose points are in tank space (blocks: +X the wearer's right, +Y up,
 * -Z toward the wearer's front, x = 0.5 the body's centre line); a standing wearer's height above the feet is
 * {@code (TANK_FLOOR + y) * MODEL_SCALE}. First-person points are in camera space (+X right, +Y up, +Z ahead).
 */
public final class ActuatorPose {
    public static final int LEFT = -1, RIGHT = 1;
    /** Player model scale, and the tank-space height of the feet below tank y = 0. */
    public static final double MODEL_SCALE = 0.9375, TANK_FLOOR = 0.876;
    /** Half the player's drawn width, arms included, and the top of the head, blocks. */
    public static final double BODY_HALF_WIDTH = 0.47, HEAD_TOP = 1.8;
    /** Actuator shoulder hubs on top of the tank (tank space, blocks). */
    public static final double SHOULDER_X = 0.25, SHOULDER_Y = 0.84, SHOULDER_Z = 0.47;
    /** Rest Pose from the shoulder: tip out, up and ahead; the elbow on the way. */
    public static final double REST_OUT = 0.45, REST_UP = 0.5, REST_AHEAD = 0.06;
    public static final double REST_ELBOW_OUT = 0.32, REST_ELBOW_UP = 0.24, REST_ELBOW_AHEAD = -0.1;
    /** Half-open claw at rest. */
    public static final float REST_OPEN = 0.35F;
    /** Ticks of one slow breath and of one idle gesture cycle (each Actuator its own phase). */
    public static final float BREATH_TICKS = 90, GESTURE_TICKS = 160, GESTURE_LENGTH = 26;
    /** First person: distance of an Actuator's root from the eye, how far below it, and the angle past the frame's edge. */
    public static final double ENTRY_DISTANCE = 0.8, ENTRY_DROP = 0.32, ENTRY_MARGIN_DEGREES = 14;
    public static final float MIN_OPACITY = 0.1F;
    /** Ticks for a Far Use reach to arc out (and back), the grab wind-up, the throw follow-through and the Fitting flex. */
    public static final float REACH_TICKS = 4, WIND_UP_TICKS = 5, FOLLOW_TICKS = 8, FLEX_TICKS = 20;

    private ActuatorPose() {
    }

    public static double shoulderX(int side) {
        return 0.5 + side * SHOULDER_X;
    }

    public static Vec3 shoulder(int side) {
        return new Vec3(shoulderX(side), SHOULDER_Y, SHOULDER_Z);
    }

    /** Slow breathing, -1..1, a different phase per side. */
    public static double breathe(float time, int side) {
        return Math.sin((time / BREATH_TICKS + (side > 0 ? 0.37 : 0)) * Math.PI * 2);
    }

    /** An occasional small gesture (lift and curl), 0 most of the time, a smooth 0..1..0 bump once per cycle. */
    public static double gesture(float time, int side) {
        float cycle = time + (side > 0 ? GESTURE_TICKS * 0.55F : 0);
        float in = cycle - Mth.floor(cycle / GESTURE_TICKS) * GESTURE_TICKS;
        if (in >= GESTURE_LENGTH) return 0;
        double s = Math.sin(in / GESTURE_LENGTH * Math.PI);
        return s * s;
    }

    /** Rest Pose tip (tank space), breathing and gesturing. */
    public static Vec3 restTip(int side, float time) {
        double b = breathe(time, side), g = gesture(time, side);
        return shoulder(side).add(side * (REST_OUT + 0.015 * b), REST_UP + 0.03 * b + 0.06 * g, -REST_AHEAD - 0.1 * g);
    }

    public static Vec3 restElbow(int side, float time) {
        double b = breathe(time, side);
        return shoulder(side).add(side * REST_ELBOW_OUT, REST_ELBOW_UP + 0.015 * b, -REST_ELBOW_AHEAD);
    }

    /** Which way the claw looks at rest (tank space): ahead, a little up and in, curling forward on a gesture. */
    public static Vec3 restLook(int side, float time) {
        double g = gesture(time, side);
        return new Vec3(-side * 0.3, 0.45 - 0.4 * g, -1).normalize();
    }

    public static float restOpen(int side, float time) {
        return (float) (REST_OPEN + 0.06 * breathe(time, side) + 0.3 * gesture(time, side));
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

    /** One tick of a 0..1 level toward on (1) or off (0), {@code 1 / ticks} per tick. */
    public static float approach(float level, boolean on, float ticks) {
        return Mth.clamp(level + (on ? 1 : -1) / Math.max(1, ticks), 0, 1);
    }

    /** Grab start: how far the arm is cocked back (0..1) {@code age} ticks in; it pulls back, then lunges to the target. */
    public static double windUp(float age) {
        if (age <= 0 || age >= WIND_UP_TICKS) return 0;
        float peak = WIND_UP_TICKS * 0.4F;
        return age < peak ? smooth(age / peak) : 1 - smooth((age - peak) / (WIND_UP_TICKS - peak));
    }

    /** Throw: how far the arm follows through along the throw (0..1) {@code age} ticks in, snapping out, then back. */
    public static double followThrough(float age) {
        if (age <= 0 || age >= FOLLOW_TICKS) return 0;
        return age < 2 ? smooth(age / 2) : 1 - smooth((age - 2) / (FOLLOW_TICKS - 2));
    }

    /** Fitting flex: folded (1) unfolding to rest (0) over the first 40% of the flex. */
    public static double folded(float age) {
        if (age < 0 || age >= FLEX_TICKS) return 0;
        return 1 - smooth(age / (FLEX_TICKS * 0.4F));
    }

    /** Fitting flex: the claws open and close twice once unfolded, 0..1. */
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
     * Elbow of an acting arm (world): part way along shoulder → tip, pushed out to the arm's side and up, more for a longer arm,
     * so it arcs rather than poking straight.
     */
    public static Vec3 elbow(Vec3 s, Vec3 t, Vec3 out, Vec3 up) {
        Vec3 d = t.subtract(s);
        double len = d.length();
        if (len < 1.0E-4) return s;
        Vec3 u = d.scale(1 / len);
        Vec3 push = out.scale(0.8).add(up.scale(0.6));
        push = push.subtract(u.scale(push.dot(u)));
        if (push.lengthSqr() < 1.0E-6) push = up.subtract(u.scale(up.dot(u)));
        if (push.lengthSqr() < 1.0E-6) return s.add(d.scale(0.42));
        double h = Mth.clamp(0.22 * len + 0.08, 0.12, 1.1);
        return s.add(d.scale(0.42)).add(push.normalize().scale(h));
    }

    /** {@code tip} pulled in to at most {@code reach} from the shoulder. */
    public static Vec3 clampReach(Vec3 s, Vec3 tip, double reach) {
        Vec3 d = tip.subtract(s);
        double len = d.length();
        return len <= reach || len < 1.0E-6 ? tip : s.add(d.scale(reach / len));
    }
}
