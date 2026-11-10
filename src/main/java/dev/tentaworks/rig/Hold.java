package dev.tentaworks.rig;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Where an Actuator holds a Grabbed mob (CONTEXT: Grab), the same rule on every side: in the camera basis (look, right,
 * up) the box centre sits the hold distance ahead (by default {@value #AHEAD} + r), {@value #LEFT} + r to the holding Actuator's
 * side ({@link #side}: left for the off hand of a right-handed wearer) and {@value #DOWN} + h/2 − {@value #RAISE} below the eye,
 * so it stays low on that side and out of the crosshair. r = w/2 · (|sin yaw| + |cos yaw|) is the half-width of the axis-aligned
 * box across the turned view (w/2 facing along an axis, up to w/√2 at a diagonal). The Grab key held with the mouse wheel moves
 * it along the look ({@link #distance}), {@value #NOTCH} per notch, from {@value #MIN_AHEAD} + r out to Reach of the body
 * ({@link #fit}). The mob never goes into a block:
 * where its box does not fit it is lifted in steps of {@value #LIFT_STEP} up to its own height (a tall mob held by a wearer on
 * the floor), else it stays where it is. Nor through one: it is swept from where it is toward that point and stops at the first
 * block in the way. A hold ends ({@link #ends}) on Crawl, the wearer's death, the Rig off or unfitted, no Air, the target gone or
 * in another level, or farther than Reach + {@value #FAR_SLACK} from the body for {@value #FAR_TICKS} ticks.
 */
public final class Hold {
    public static final double AHEAD = 1.1;
    public static final double LEFT = 0.7;
    public static final double DOWN = 0.55;
    public static final double RAISE = 0.3;
    public static final double LIFT_STEP = 0.25;
    public static final double FAR_SLACK = 1.0;
    public static final int FAR_TICKS = 5;
    public static final double NOTCH = 0.5;
    public static final double MIN_AHEAD = 0.5;
    /** Most wheel notches one packet may carry. */
    public static final int MAX_NOTCHES = 10;
    /** Nearest a held Ship point comes along the look. */
    public static final double SHIP_MIN = 1.0;
    /** Camera side of a hold: left (+1) or right (−1). */
    public static final int LEFT_SIDE = 1, RIGHT_SIDE = -1;

    /**
     * What the server sees of a hold this tick; {@code farTicks} counts ticks in a row beyond Reach + {@value #FAR_SLACK},
     * {@code handFull} an item is in the hand of a holding Actuator, {@code underfoot} the held Ship is all that holds the wearer up
     * ({@link GrabRule#holdsUp}: their feet rest on it).
     */
    public record Status(boolean wearerAlive, boolean fitted, boolean air, boolean crawl, boolean targetGone, boolean sameLevel, int farTicks,
                         boolean handFull, boolean underfoot) {
    }

    private Hold() {
    }

    /** The box centre of a held target of {@code width} × {@code height} held on the left at the default distance. */
    public static Vec3 point(Vec3 eye, Vec3 look, float yRot, double width, double height) {
        return point(eye, look, yRot, width, height, LEFT_SIDE, Double.NaN);
    }

    /**
     * The box centre of a held target of {@code width} × {@code height} for a wearer with this eye, look and yaw (degrees), on camera
     * {@code side} ({@link #LEFT_SIDE}, {@link #RIGHT_SIDE}), {@code distance} along the look (NaN: the default).
     */
    public static Vec3 point(Vec3 eye, Vec3 look, float yRot, double width, double height, int side, double distance) {
        Vec3[] b = basis(look, yRot);
        double half = half(width, yRot);
        double ahead = Double.isNaN(distance) ? AHEAD + half : distance;
        return eye.add(b[0].scale(ahead)).subtract(b[1].scale(side * (LEFT + half))).subtract(b[2].scale(DOWN + height / 2 - RAISE));
    }

    /** Half-width of the axis-aligned box across the view turned by {@code yRot} degrees. */
    static double half(double width, float yRot) {
        float yaw = yRot * Mth.DEG_TO_RAD;
        return width / 2 * (Math.abs(Mth.sin(yaw)) + Math.abs(Mth.cos(yaw)));
    }

    /** The camera side of a hold on body side {@code arm}. */
    public static int side(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? LEFT_SIDE : RIGHT_SIDE;
    }

    /** The default hold distance along the look (NaN stored = this). */
    public static double defaultDistance(double width, float yRot) {
        return AHEAD + half(width, yRot);
    }

    /** The nearest hold: the box clear of the wearer's body for its size. */
    public static double minDistance(double width, float yRot) {
        return MIN_AHEAD + half(width, yRot);
    }

    /**
     * A new hold distance after {@code notches} of the wheel (positive: farther) from {@code distance} (NaN: the default), between
     * {@link #minDistance} and {@code reach}; at most {@value #MAX_NOTCHES} notches count.
     */
    public static double distance(double distance, int notches, double width, float yRot, double reach) {
        double from = Double.isNaN(distance) ? defaultDistance(width, yRot) : distance;
        int n = Mth.clamp(notches, -MAX_NOTCHES, MAX_NOTCHES);
        double min = minDistance(width, yRot);
        return Mth.clamp(from + n * NOTCH, min, Math.max(min, reach));
    }

    /**
     * The farthest distance up to {@code distance} (NaN: the default) at which the held box centre stays within {@code reach} of
     * the body, never nearer than {@link #minDistance}: a hold far out along the look is pulled in where the body is farther away.
     */
    public static double fit(Vec3 eye, Vec3 look, float yRot, double width, double height, int side, double distance, Vec3 body, double reach) {
        double min = minDistance(width, yRot);
        double d = Math.max(min, Double.isNaN(distance) ? defaultDistance(width, yRot) : distance);
        double r2 = reach * reach;
        if (point(eye, look, yRot, width, height, side, d).distanceToSqr(body) <= r2) return d;
        if (point(eye, look, yRot, width, height, side, min).distanceToSqr(body) > r2) return min;
        double lo = min, hi = d;
        for (int i = 0; i < 16; i++) {
            double mid = (lo + hi) / 2;
            if (point(eye, look, yRot, width, height, side, mid).distanceToSqr(body) <= r2) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /**
     * A held Ship point {@code rel} ({@link #shipRel}) moved {@code delta} along the look, between {@value #SHIP_MIN} and where
     * it is {@code max} from the eye; across the view it stays.
     */
    public static Vec3 shipAlong(Vec3 rel, double delta, double max) {
        double side2 = rel.y * rel.y + rel.z * rel.z;
        double far = Math.sqrt(Math.max(0, max * max - side2));
        return new Vec3(Math.min(Math.max(SHIP_MIN, rel.x + delta), far), rel.y, rel.z);
    }

    /** The camera basis for this look and yaw (degrees): look direction, right, up. */
    static Vec3[] basis(Vec3 look, float yRot) {
        double len = look.length();
        float yaw = yRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 dir = len < 1.0E-6 ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)) : look.scale(1 / len);
        return new Vec3[] {dir, right, right.cross(dir)};
    }

    /**
     * A held Ship point {@code held} in the camera basis at the start (along the look, right, up), no farther than {@code max}
     * from the eye: {@link #shipPoint} puts it back in front of the eye wherever the wearer looks.
     */
    public static Vec3 shipRel(Vec3 eye, Vec3 look, float yRot, Vec3 held, double max) {
        Vec3[] b = basis(look, yRot);
        Vec3 d = held.subtract(eye);
        Vec3 rel = new Vec3(d.dot(b[0]), d.dot(b[1]), d.dot(b[2]));
        double len = rel.length();
        return len > max && len > 1.0E-9 ? rel.scale(max / len) : rel;
    }

    /** Where the held Ship point goes this tick: {@code rel} ({@link #shipRel}) in this tick's camera basis. */
    public static Vec3 shipPoint(Vec3 eye, Vec3 look, float yRot, Vec3 rel) {
        Vec3[] b = basis(look, yRot);
        return eye.add(b[0].scale(rel.x)).add(b[1].scale(rel.y)).add(b[2].scale(rel.z));
    }

    /** The held Ship's orientation after the wearer turned by {@code yawDelta} degrees since the start: yaw follows, pitch and roll stay. */
    public static Quaterniond shipTurn(Quaterniondc start, double yawDelta) {
        return new Quaterniond().rotateY(-Math.toRadians(yawDelta)).mul(start);
    }

    /** The first feet height from {@code feetY} up, in steps of {@value #LIFT_STEP} up to {@code height}, where the box {@code fits}; NaN when none does. */
    public static double lift(double feetY, double height, DoublePredicate fits) {
        int steps = Mth.ceil(height / LIFT_STEP);
        for (int i = 0; i <= steps; i++) {
            double y = feetY + i * LIFT_STEP;
            if (fits.test(y)) return y;
        }
        return Double.NaN;
    }

    /** Feet at {@code feet}, lifted until the box fits ({@link #lift}), else where the target is (its last free spot). */
    public static Vec3 place(Level level, Entity target, Vec3 feet) {
        AABB box = target.getDimensions(target.getPose()).makeBoundingBox(feet);
        double y = lift(feet.y, target.getBbHeight(), at -> level.noCollision(target, box.move(0, at - feet.y, 0)));
        return Double.isNaN(y) ? target.position() : new Vec3(feet.x, y, feet.z);
    }

    /** Where {@code target} ends moving from its feet toward {@code feet} with block collision, as vanilla movement collides. */
    public static Vec3 sweep(Level level, Entity target, Vec3 feet) {
        Vec3 from = target.position(), d = feet.subtract(from);
        if (d.lengthSqr() < 1.0E-12) return feet;
        return from.add(Entity.collideBoundingBox(target, d, target.getBoundingBox(), level, List.of()));
    }

    /** Beyond Reach + {@value #FAR_SLACK}: the far rule counts this tick. */
    public static boolean far(double distance, double reach) {
        return distance > reach + FAR_SLACK;
    }

    /**
     * Why the hold ends now (the release reason), or null while it goes on: "hand" when an item lands in a holding Actuator's
     * hand (pickup, swap, hotbar change), "underfoot" when the wearer stands on the Ship they hold (it would carry them, and they
     * it). Stow, Setting Down, Rides, glides and Falling are not on the list.
     */
    public static @Nullable String ends(Status s) {
        if (!s.wearerAlive()) return "death";
        if (!s.fitted()) return "rig_off";
        if (s.crawl()) return "crawl";
        if (!s.air()) return "air";
        if (s.targetGone()) return "gone";
        if (!s.sameLevel()) return "dimension";
        if (s.farTicks() >= FAR_TICKS) return "far";
        if (s.handFull()) return "hand";
        if (s.underfoot()) return "underfoot";
        return null;
    }
}
