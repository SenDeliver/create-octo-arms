package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
    /** Ticks of the grab wind-up, the throw snap and return, and the Fitting flex. */
    public static final float WIND_UP_TICKS = 5, SNAP_TICKS = 2, FOLLOW_TICKS = 16, FLEX_TICKS = 20;
    /**
     * Time constants (ticks) of the chases ({@link #chase}): an arm going out to act, coming back, its tip following a new
     * target, and every other change of pose. Three time constants are 95% of the way: out and onto a new target in under two
     * ticks, the rest within {@link #RETARGET_TICKS}.
     */
    public static final double ACT_TAU = 0.5, BACK_TAU = 1.1, AIM_TAU = 0.5, POSE_TAU = 1.0, IDLE_TAU = 2.2;
    /** A new target counts as reached this many ticks after the crosshair moved to it: at most {@link #RETARGET_LEFT} of the way is left. */
    public static final double RETARGET_TICKS = 3, RETARGET_LEFT = 0.01;
    /** Swing progress (0..1, vanilla's attack animation) at the end of the wind-up, at the impact and at the end of the rebound. */
    public static final double STRIKE_WIND = 0.12, STRIKE_IMPACT = 0.32, STRIKE_REBOUND = 0.5;
    /** Between swings a tool waits a little cocked ({@link #strikeSwing}) and pulled back from the target ({@link #strikeBack}). */
    public static final double READY_SWING = -0.25, READY_BACK = 0.5;
    /** Swing progress at which a thrust (a use, a place, a punch) touches the target. */
    public static final double THRUST_PEAK = 0.3;
    /** Vanilla's swing while mining restarts every 4 ticks; a Dual Wield melee strike of the off hand starts this many ticks after the main one. */
    public static final double MINING_CYCLE = 4, DUAL_BEAT = 3, SWING_TICKS = 6;
    /** Gripper length from the wrist to the spot between the fingers where an item is held, blocks. */
    public static final double GRIP_LENGTH = 0.245;
    /** A held tool from the spot it is gripped at to its head, blocks at full size, and how far it rears back for a strike, radians. */
    public static final double TOOL_LENGTH = 0.62, STRIKE_ARC = 1.45;

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

    /** Eye height of a standing wearer over the feet (blocks). */
    public static final double STANDING_EYE = 1.62;
    private static final Matrix4f TANK_TO_EYE = tankToEye();

    /** Tank space of a standing Rig whose body faces the look, seen from the eye: camera space (x right, y up, z ahead). */
    private static Matrix4f tankToEye() {
        PoseStack ps = new PoseStack();
        ps.scale(-1, 1, 1);
        ps.translate(0, -STANDING_EYE, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        TentacleRenderer.bodyToTank(ps);
        return new Matrix4f(ps.last().pose());
    }

    /**
     * First person: a tank-space point of a body-held pose (Rest, folded, limp) in camera space, as a standing wearer facing the
     * look holds it, so the body's turn, a strafe, a crouch or a swim never bring it on screen.
     */
    public static Vec3 eyeView(Vec3 tank) {
        Vector3f v = TANK_TO_EYE.transformPosition((float) tank.x, (float) tank.y, (float) tank.z, new Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    /** A tank-space direction of a body-held pose in camera space ({@link #eyeView}), unit length. */
    public static Vec3 eyeDirection(Vec3 tank) {
        Vector3f v = TANK_TO_EYE.transformDirection((float) tank.x, (float) tank.y, (float) tank.z, new Vector3f()).normalize();
        return new Vec3(v.x, v.y, v.z);
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

    /** Which side the Actuator of a hand is on: the main hand's on the wearer's main-arm side, the off hand's on the other. */
    public static int side(boolean rightHanded, boolean mainHand) {
        return rightHanded == mainHand ? RIGHT : LEFT;
    }

    /** How much of the way to its goal a chased value covers in {@code dt} ticks with time constant {@code tau}: the same at any frame rate. */
    public static double chase(double dt, double tau) {
        return dt <= 0 ? 0 : 1 - Math.exp(-dt / tau);
    }

    /**
     * A tool's swing about the gripper for a swing progress 0..1: -1 wound back, +1 on the target. It waits a little cocked
     * ({@link #READY_SWING}), winds back fast ({@link #STRIKE_WIND}), whips forward speeding up into the impact
     * ({@link #STRIKE_IMPACT}), bounces off and comes back to ready; 0 and 1 are the same pose, so held mining loops.
     */
    public static double strikeSwing(double p) {
        p = Mth.clamp(p, 0, 1);
        if (p < STRIKE_WIND) return Mth.lerp(smooth(p / STRIKE_WIND), READY_SWING, -1);
        if (p < STRIKE_IMPACT) {
            double k = (p - STRIKE_WIND) / (STRIKE_IMPACT - STRIKE_WIND);
            return -1 + 2 * k * k;
        }
        if (p < STRIKE_REBOUND) return Mth.lerp(smooth((p - STRIKE_IMPACT) / (STRIKE_REBOUND - STRIKE_IMPACT)), 1, 0.7);
        return Mth.lerp(smooth((p - STRIKE_REBOUND) / (1 - STRIKE_REBOUND)), 0.7, READY_SWING);
    }

    /** How far the gripper is pulled back from where it stands at the impact, 0..1, for a swing progress 0..1: back on the wind-up, 0 on the impact. */
    public static double strikeBack(double p) {
        p = Mth.clamp(p, 0, 1);
        if (p < STRIKE_WIND) return Mth.lerp(smooth(p / STRIKE_WIND), READY_BACK, 1);
        if (p < STRIKE_IMPACT) {
            double k = (p - STRIKE_WIND) / (STRIKE_IMPACT - STRIKE_WIND);
            return 1 - k * k;
        }
        if (p < STRIKE_REBOUND) return Mth.lerp(smooth((p - STRIKE_IMPACT) / (STRIKE_REBOUND - STRIKE_IMPACT)), 0, 0.2);
        return Mth.lerp(smooth((p - STRIKE_REBOUND) / (1 - STRIKE_REBOUND)), 0.2, READY_BACK);
    }

    /** A thrust for a swing progress 0..1: 0 ready, 1 on the target at {@link #THRUST_PEAK}, then back. */
    public static double thrust(double p) {
        p = Mth.clamp(p, 0, 1);
        return p < THRUST_PEAK ? easeOut(p / THRUST_PEAK) : 1 - smooth((p - THRUST_PEAK) / (1 - THRUST_PEAK));
    }

    /** Vanilla's swing progress {@code age} ticks into a held mining swing: half of it in three ticks, the rest in the fourth, then again. */
    public static double miningSwing(double age) {
        double t = age - Math.floor(age / MINING_CYCLE) * MINING_CYCLE;
        return t < MINING_CYCLE - 1 ? t / SWING_TICKS : 0.5 + (t - (MINING_CYCLE - 1)) * 0.5;
    }

    /** Dual Wield mining: the off-hand Actuator's swing progress {@code age} ticks after the main swing began: half a cycle behind, so the two strike in turn. */
    public static double dualMining(double age) {
        return miningSwing(age + MINING_CYCLE / 2);
    }

    /** Dual Wield melee: the off-hand Actuator's swing progress {@code age} ticks after the main swing began: a beat later; -1 when it is not swinging. */
    public static double dualMelee(double age) {
        double p = (age - DUAL_BEAT) / SWING_TICKS;
        return p < 0 || p >= 1 ? -1 : p;
    }

    /**
     * Where a hand's item is held, in view space (x toward the Actuator's own side, y up, z ahead of the eye, blocks; first
     * person: at the hand pass's 70 degree view): the spot, how far the item tips forward (radians), and how much the gripper
     * turns from its arm's line to the look (0..1).
     */
    public record Grip(Vec3 at, double tilt, double along) {
    }

    /** First person: where the own wearer's empty main-hand gripper waits, view space. */
    public static final Vec3 BARE = new Vec3(0.7, -0.52, 0.86);

    /** A carried item at rest: where a hand would hold it, low on its own side. */
    public static Grip carry(boolean first) {
        return first ? new Grip(new Vec3(0.62, -0.38, 0.9), 0, 0) : new Grip(new Vec3(0.4, -0.3, 0.62), 0, 0);
    }

    /**
     * A held use by its animation, {@code used} ticks in: food and drink come to the mouth and bob, a shield is raised in front,
     * a bow or crossbow is aimed along the look (a bow shaking a little at full draw), a spear is cocked back over the shoulder,
     * a spyglass goes to the eye, a horn to the mouth tipped up; anything else is drawn in a little.
     */
    public static Grip use(UseAnim anim, boolean first, float used) {
        double in = easeOut(used / 4);
        return switch (anim) {
            case EAT, DRINK -> {
                double bob = 0.03 * Math.sin(used * 1.57) * in;
                yield first ? new Grip(new Vec3(0.24, -0.36 + bob, 0.8), 0.5, 0.6) : new Grip(new Vec3(0.07, -0.16 + bob, 0.4), 0.5, 0.6);
            }
            case BLOCK -> first ? new Grip(new Vec3(0.34, -0.24, 0.72), 0, 0.8) : new Grip(new Vec3(0.2, -0.3, 0.72), 0, 0.8);
            case BOW -> {
                double pull = Mth.clamp(used / 20, 0, 1);
                double shake = pull >= 1 ? 0.006 * Math.sin(used * 2.6) : 0;
                yield first ? new Grip(new Vec3(0.4, -0.26 + shake, 1.0 - 0.1 * pull), 0, 1) : new Grip(new Vec3(0.32, -0.16 + shake, 0.85 - 0.1 * pull), 0, 1);
            }
            case CROSSBOW -> first ? new Grip(new Vec3(0.36, -0.3, 0.8), 0.25, 1) : new Grip(new Vec3(0.26, -0.26, 0.66), 0.25, 1);
            case SPEAR -> first ? new Grip(new Vec3(0.66, 0.12, 0.62), 1.45, 1) : new Grip(new Vec3(0.42, 0.34, -0.1), 1.45, 1);
            case SPYGLASS -> first ? carry(true) : new Grip(new Vec3(0.12, -0.02, 0.36), 1.2, 1);
            case TOOT_HORN -> first ? new Grip(new Vec3(0.24, -0.34, 0.8), -0.4, 0.6) : new Grip(new Vec3(0.08, -0.14, 0.42), -0.4, 0.6);
            case BRUSH -> {
                double sweep = 0.12 * Math.sin(used * 0.8) * in;
                yield first ? new Grip(new Vec3(0.34 + sweep, -0.3, 0.95), 0.9, 0.5) : new Grip(new Vec3(0.26 + sweep, -0.36, 0.7), 0.9, 0.5);
            }
            default -> first ? new Grip(new Vec3(0.5, -0.34, 0.8), 0, 0.3) : new Grip(new Vec3(0.32, -0.28, 0.56), 0, 0.3);
        };
    }

    /** Guard flinch for a hurt timer running {@code hurt} of {@code duration} ticks down: 1 at the blow, easing to 0. */
    public static double flinch(float hurt, int duration) {
        if (hurt <= 0 || duration <= 0) return 0;
        double k = Mth.clamp(hurt / duration, 0, 1);
        return k * k;
    }

    /** Brace for a fall: 0 up to 0.6 blocks per tick downward, 1 from 1.2. */
    public static double brace(double fallSpeed) {
        return smooth((fallSpeed - 0.6) / 0.6);
    }

    /** Low Air: 0 above a tenth of the tank, 1 at empty. */
    public static double lowAir(double fraction) {
        return smooth(1 - fraction / 0.1);
    }

    /** An occasional 0..1..0 bump {@code length} ticks long once per {@code every} ticks, shifted by {@code seed}. */
    public static double bump(float time, float every, float length, int seed) {
        float t = time + (seed & 0xFF) * every / 256F;
        float in = t - Mth.floor(t / every) * every;
        if (in >= length) return 0;
        double s = Math.sin(in / length * Math.PI);
        return s * s;
    }

    /** The plane a tool swings in: its axis (grip to head) at the impact and the way it rears back, unit and at right angles. */
    public record Swing(Vec3 impact, Vec3 back) {
        /** {@code back} is squared up to {@code impact}; a hint along it falls back to straight up, then sideways. */
        public static Swing of(Vec3 impact, Vec3 back) {
            Vec3 i = unit(impact, new Vec3(0, 0, 1));
            Vec3 b = off(back, i);
            if (b.lengthSqr() < 1.0E-8) b = off(new Vec3(0, 1, 0), i);
            if (b.lengthSqr() < 1.0E-8) b = off(new Vec3(1, 0, 0), i);
            return new Swing(i, b.normalize());
        }
    }

    /**
     * A held item this frame: the spot it is gripped at, its axis (grip to head, unit), the way its edge faces (unit, across the
     * axis) and the way the gripper holding it points (unit).
     */
    public record Tool(Vec3 grip, Vec3 axis, Vec3 edge, Vec3 palm) {
        public Vec3 head(double length) {
            return grip.add(axis.scale(length));
        }
    }

    /**
     * A strike at {@code target} with a tool {@code length} long, at swing progress {@code p}: at the impact the head is on the
     * target with the tool along the plane's impact axis; before it the tool rears back by up to {@link #STRIKE_ARC} and the
     * grip pulls away by up to {@code pull}; the edge leads the head. The gripper is rigid on the handle and turns with it: it
     * holds from behind the swing, or from the plane's side by {@code sideways} (-1..1, the sign picks the side), so it never
     * flips mid-swing.
     */
    public static Tool strikeAt(Vec3 target, Swing plane, double length, double p, Vec3 pull, double sideways) {
        double phi = (1 - strikeSwing(p)) / 2 * STRIKE_ARC;
        double c = Math.cos(phi), s = Math.sin(phi);
        Vec3 axis = plane.impact().scale(c).add(plane.back().scale(s));
        Vec3 edge = plane.impact().scale(s).subtract(plane.back().scale(c));
        Vec3 grip = target.subtract(plane.impact().scale(length)).add(pull.scale(strikeBack(p)));
        Vec3 behind = axis.scale(0.5).subtract(edge.scale(0.85));
        Vec3 palm = plane.impact().cross(plane.back()).scale(sideways).add(behind.scale(1 - Math.abs(sideways))).normalize();
        return new Tool(grip, axis, edge, palm);
    }

    /** A wrist and the way its gripper points (unit). */
    public record Hand(Vec3 wrist, Vec3 dir) {
    }

    /**
     * The way a gripper points that closes square across an item's {@code axis} at {@code grip}, as a hand closes round a handle:
     * from the arm's side of it (the arm comes from {@code root}), turned toward {@code toward} first by {@code along}, 0..1.
     */
    public static Vec3 palm(Vec3 root, Vec3 grip, Vec3 axis, Vec3 toward, double along) {
        Vec3 u = unit(grip.subtract(root), new Vec3(0, 0, 1));
        Vec3 from = along <= 0 ? u : unit(u.scale(1 - along).add(unit(toward, u).scale(along)), u);
        Vec3 a = unit(axis, new Vec3(0, 1, 0));
        Vec3 square = off(from, a);
        if (square.lengthSqr() < 1.0E-6) square = off(u, a);
        if (square.lengthSqr() < 1.0E-6) square = off(Math.abs(a.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0), a);
        return square.normalize();
    }

    /**
     * The wrist for a gripper whose fingers meet on {@code grip}, coming from {@code root}. {@code held} 0: in line with the arm;
     * {@code held} 1: pointing along {@code palm}.
     */
    public static Hand hand(Vec3 root, Vec3 grip, Vec3 palm, double held) {
        Vec3 u = unit(grip.subtract(root), new Vec3(0, 0, 1));
        if (held <= 0) return new Hand(grip.subtract(u.scale(GRIP_LENGTH)), u);
        Vec3 dir = unit(u.scale(1 - held).add(unit(palm, u).scale(held)), u);
        return new Hand(grip.subtract(dir.scale(GRIP_LENGTH)), dir);
    }

    /** {@link #hand(Vec3, Vec3, Vec3, double)} with the gripper square across the item's {@code axis} ({@link #palm}). */
    public static Hand hand(Vec3 root, Vec3 grip, Vec3 axis, Vec3 toward, double along, double held) {
        return hand(root, grip, held <= 0 ? toward : palm(root, grip, axis, toward, along), held);
    }
}
