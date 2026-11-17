package dev.tentaworks.rig;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
    /** Overhead: the held Ship's lowest point this far above the eye, its held face this far ahead of the eye on the level. */
    public static final double OVERHEAD_CLEAR = 0.75, OVERHEAD_FORWARD = 0.5;
    /** Most degrees one Turn packet turns a held Ship about each axis. */
    public static final double TURN_MAX = 30;
    /** Half the span between the two Actuator tips on a held Ship's face, along its face tangent. */
    public static final double GRIP_SPREAD = 0.4;
    /** Most degrees a held Ship turns a tick from the banked Turn with both tips holding ({@link #turnStep}); half that while one reaches. */
    public static final double TURN_RATE = 9;
    /** Most degrees of Turn banked about each axis, not yet turned. */
    public static final double TURN_BANK = 90;
    /** Share of the hold's correction after a new pivot kept each tick ({@link #settle}), and the most it moves a tick (blocks). */
    public static final double SETTLE = 0.7, SETTLE_STEP = 0.25;
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

    /** A held Ship's hold point: {@code rel} in front of the eye ({@link #shipRel}), or Overhead ({@link #shipOverhead}). */
    public record ShipHold(Vec3 rel, boolean overhead) {
    }

    /**
     * The wheel on a held Ship: {@code notches} along the look ({@link #shipAlong}, at most {@value #MAX_NOTCHES}). Farther with the
     * point already at its farthest ({@code max} from the eye) goes Overhead; nearer from Overhead comes back to that farthest spot.
     */
    public static ShipHold shipWheel(ShipHold hold, int notches, double max) {
        int n = Mth.clamp(notches, -MAX_NOTCHES, MAX_NOTCHES);
        if (n == 0) return hold;
        Vec3 far = shipAlong(hold.rel(), Double.MAX_VALUE / 4, max);
        if (hold.overhead()) return n < 0 ? new ShipHold(far, false) : hold;
        if (n > 0 && hold.rel().x >= far.x - 1.0E-6) return new ShipHold(far, true);
        return new ShipHold(shipAlong(hold.rel(), n * NOTCH, max), false);
    }

    /**
     * Where the held face goes Overhead: {@value #OVERHEAD_FORWARD} ahead of the eye on the level of the wearer's yaw (pitch does
     * not move it) and high enough that the hull's lowest point, {@code aboveBottom} below the face, is {@value #OVERHEAD_CLEAR}
     * above the eye; lower where that would be farther than {@code max} from {@code body}, never above it.
     */
    public static Vec3 shipOverhead(Vec3 eye, float yRot, double aboveBottom, Vec3 body, double max) {
        float yaw = yRot * Mth.DEG_TO_RAD;
        Vec3 ahead = eye.add(-Mth.sin(yaw) * OVERHEAD_FORWARD, 0, Mth.cos(yaw) * OVERHEAD_FORWARD);
        double y = eye.y + OVERHEAD_CLEAR + Math.max(0, aboveBottom);
        double dx = ahead.x - body.x, dz = ahead.z - body.z, h2 = dx * dx + dz * dz;
        if (h2 >= max * max) {
            double k = max / Math.sqrt(h2);
            return new Vec3(body.x + dx * k, body.y, body.z + dz * k);
        }
        return new Vec3(ahead.x, Math.min(y, body.y + Math.sqrt(max * max - h2)), ahead.z);
    }

    /** How far the lowest corner of {@code bounds} sits below {@code face}, both in the Ship's own space, once turned to {@code orientation}. */
    public static double shipBelow(Quaterniondc orientation, Vec3 face, AABB bounds) {
        double below = Double.NEGATIVE_INFINITY;
        Vector3d d = new Vector3d();
        for (int i = 0; i < 8; i++) {
            d.set(face.x - ((i & 1) == 0 ? bounds.minX : bounds.maxX), face.y - ((i & 2) == 0 ? bounds.minY : bounds.maxY),
                    face.z - ((i & 4) == 0 ? bounds.minZ : bounds.maxZ));
            below = Math.max(below, orientation.transform(d).y);
        }
        return below;
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
        return shipWant(start, yawDelta, new Quaterniond());
    }

    /** The held Ship's orientation: the wearer's Turn {@code user} on the start orientation, then the wearer's yaw since the start ({@link #shipTurn}). */
    public static Quaterniond shipWant(Quaterniondc start, double yawDelta, Quaterniondc user) {
        return new Quaterniond().rotateY(-Math.toRadians(yawDelta)).mul(user).mul(start);
    }

    /**
     * A Turn input on the wearer's {@code user} rotation ({@link #shipWant}), degrees each clamped to {@value #TURN_MAX}: {@code dYaw}
     * about world up (positive: the face toward the eye turns to the wearer's right), then {@code dPitch} about the wearer's horizontal
     * right (positive, the mouse down: that face turns down). The pitch axis is taken at {@code startYaw}, the wearer's yaw at the
     * start: once the yaw since the start is applied it is the horizontal right of the wearer's yaw now.
     */
    public static Quaterniond shipTurnInput(Quaterniondc user, float startYaw, double dYaw, double dPitch) {
        double yaw = Math.toRadians(Mth.clamp(dYaw, -TURN_MAX, TURN_MAX)), pitch = Math.toRadians(Mth.clamp(dPitch, -TURN_MAX, TURN_MAX));
        float s = startYaw * Mth.DEG_TO_RAD;
        Quaterniond out = new Quaterniond().rotateY(yaw).mul(user);
        return new Quaterniond().rotateAxis(pitch, -Mth.cos(s), 0, -Mth.sin(s)).mul(out);
    }

    /** A Turn input ({@code dYaw}, {@code dPitch}, each clamped to {@value #TURN_MAX}) added to the {@code bank} (yaw, pitch), each kept within {@value #TURN_BANK}. */
    public static double[] turnBank(double[] bank, double dYaw, double dPitch) {
        return new double[] {Mth.clamp(bank[0] + Mth.clamp(dYaw, -TURN_MAX, TURN_MAX), -TURN_BANK, TURN_BANK),
                Mth.clamp(bank[1] + Mth.clamp(dPitch, -TURN_MAX, TURN_MAX), -TURN_BANK, TURN_BANK)};
    }

    /** This tick's share of the {@code bank} (yaw, pitch): at most {@value #TURN_RATE} each, half while a tip reaches. */
    public static double[] turnStep(double[] bank, boolean reaching) {
        double rate = reaching ? TURN_RATE / 2 : TURN_RATE;
        return new double[] {Mth.clamp(bank[0], -rate, rate), Mth.clamp(bank[1], -rate, rate)};
    }

    /**
     * The shoulder of the Actuator on camera {@code side} ({@code -1} left, {@code +1} right) of a wearer at {@code feet},
     * {@code height} tall, turned {@code yRot}: on top of the tank, a little behind the body.
     */
    public static Vec3 shoulder(Vec3 feet, float yRot, double height, int side) {
        float yaw = yRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        return new Vec3(feet.x + rx * side * 0.25 - fx * 0.2, feet.y + height * 0.8, feet.z + rz * side * 0.25 - fz * 0.2);
    }

    /** The level basis of the wearer's yaw (degrees): ahead, right, up. */
    static Vec3[] yawBasis(float yRot) {
        float yaw = yRot * Mth.DEG_TO_RAD;
        return new Vec3[] {new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), new Vec3(0, 1, 0)};
    }

    /** {@code d} in the basis {@code b}. */
    static Vec3 in(Vec3[] b, Vec3 d) {
        return new Vec3(d.dot(b[0]), d.dot(b[1]), d.dot(b[2]));
    }

    /** {@code d} of the basis {@code b} in the world. */
    static Vec3 out(Vec3[] b, Vec3 d) {
        return b[0].scale(d.x).add(b[1].scale(d.y)).add(b[2].scale(d.z));
    }

    /** Where a held Ship's body goes for its point {@code local} to sit on {@code point}, turned to {@code want} ({@code pivot}: the Pose's). */
    public static Vec3 shipAim(Vec3 point, Quaterniondc want, Vec3 local, Vector3dc pivot) {
        Vector3d arm = want.transform(new Vector3d(local.x - pivot.x(), local.y - pivot.y(), local.z - pivot.z()), new Vector3d());
        return point.subtract(arm.x, arm.y, arm.z);
    }

    /** Where {@code local} is with the Ship's body at {@code aim} turned to {@code want} ({@code pivot}: the Pose's). */
    public static Vec3 shipWorld(Vec3 aim, Quaterniondc want, Vec3 local, Vector3dc pivot) {
        Vector3d arm = want.transform(new Vector3d(local.x - pivot.x(), local.y - pivot.y(), local.z - pivot.z()), new Vector3d());
        return aim.add(arm.x, arm.y, arm.z);
    }

    /**
     * The hold's correction after the pivot moved, in the hold's basis {@code b} (the camera's in front, the yaw's Overhead): from
     * where the hold puts the new pivot now ({@code base}) to where it was ({@code was}), so the Ship does not jump. {@link #settle}
     * takes it back to the hold.
     */
    public static Vec3 correction(Vec3[] b, Vec3 base, Vec3 was) {
        return in(b, was.subtract(base));
    }

    /** The hold point with its correction {@code c} in the basis {@code b}. */
    public static Vec3 corrected(Vec3[] b, Vec3 base, Vec3 c) {
        return base.add(out(b, c));
    }

    /** The correction a tick later: {@value #SETTLE} of it kept, at most {@value #SETTLE_STEP} taken a tick, zero once small. */
    public static Vec3 settle(Vec3 c) {
        double len = c.length();
        if (len < 1.0E-3) return Vec3.ZERO;
        double step = Math.min(len * (1 - SETTLE), SETTLE_STEP);
        return c.scale((len - step) / len);
    }

    /**
     * The in-face direction of a held Ship's {@code face} (both in the Ship's Frame) closest to {@code right}, the wearer's right in
     * that Frame: the two Actuator tips grip {@value #GRIP_SPREAD} to either side of the face centre along it. With {@code right}
     * along the face normal, the face's first in-face direction.
     */
    public static Direction faceTangent(Direction face, Vec3 right) {
        Direction best = null;
        double most = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == face.getAxis()) continue;
            double dot = d.getStepX() * right.x + d.getStepY() * right.y + d.getStepZ() * right.z;
            if (best == null || dot > most + 1.0E-9) {
                best = d;
                most = dot;
            }
        }
        return best;
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
