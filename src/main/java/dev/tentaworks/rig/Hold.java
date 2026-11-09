package dev.tentaworks.rig;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Where the Left Actuator holds a Grabbed mob (CONTEXT: Grab), the same rule on every side: in the camera basis (look, right,
 * up) the box centre sits {@value #AHEAD} + w/2 ahead, {@value #LEFT} + w/2 to the left and {@value #DOWN} + h/2 −
 * {@value #RAISE} below the eye, so it stays low on the wearer's left and out of the crosshair. The mob never goes into a block:
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

    /** What the server sees of a hold this tick; {@code farTicks} counts ticks in a row beyond Reach + {@value #FAR_SLACK}. */
    public record Status(boolean wearerAlive, boolean fitted, boolean air, boolean crawl, boolean targetGone, boolean sameLevel, int farTicks) {
    }

    private Hold() {
    }

    /** The box centre of a held target of {@code width} × {@code height} for a wearer with this eye, look and yaw (degrees). */
    public static Vec3 point(Vec3 eye, Vec3 look, float yRot, double width, double height) {
        double len = look.length();
        float yaw = yRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 dir = len < 1.0E-6 ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)) : look.scale(1 / len);
        Vec3 up = right.cross(dir);
        return eye.add(dir.scale(AHEAD + width / 2)).subtract(right.scale(LEFT + width / 2)).subtract(up.scale(DOWN + height / 2 - RAISE));
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

    /** Why the hold ends now (the release reason), or null while it goes on. Stow, Setting Down, Rides, glides and Falling are not on the list. */
    public static @Nullable String ends(Status s) {
        if (!s.wearerAlive()) return "death";
        if (!s.fitted()) return "rig_off";
        if (s.crawl()) return "crawl";
        if (!s.air()) return "air";
        if (s.targetGone()) return "gone";
        if (!s.sameLevel()) return "dimension";
        if (s.farTicks() >= FAR_TICKS) return "far";
        return null;
    }
}
