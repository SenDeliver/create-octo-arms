package dev.tentaworks.rig;

import dev.tentaworks.compat.ShipFrames;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Set Down at the looked spot (CONTEXT: Grab), the same rule on client and server. The crosshair ray from the eye, as far as
 * the Actuators reach ({@link ActuatorReach#pickRange}), hits a block face: on a top face the held mob stands there; on a side
 * face it goes beside it (lifted out of the floor as a hold is) and drops to the floor below, at most {@value #DROP} blocks;
 * a bottom face, a Ship block or no hit gives no spot. A spot is valid when the mob's box fits there, keeps clear of the wearer,
 * comes within Reach (+ slack on the server) of the body and is in sight of it as a Grip must be. The Grab key then carries the
 * mob there in {@value #CARRY_TICKS} ticks, swept with block collision, and sets it down.
 */
public final class SetDown {
    public static final double DROP = 3.0;
    public static final int CARRY_TICKS = 4;
    private static final double GAP = 0.01;
    private static final double STEP = 0.25;

    /** A spot: the feet (bottom centre) of the held box there, the box, and whether the mob may be set down there. */
    public record Spot(Vec3 feet, AABB box, boolean valid) {
    }

    private SetDown() {
    }

    /**
     * Where a box {@code width} × {@code height} goes for a hit at {@code hit} on {@code face} ({@code free}: the box fits there),
     * or null for none (bottom face, or no floor within {@value #DROP} beside a side face).
     */
    public static @Nullable Vec3 feet(Vec3 hit, Direction face, double width, double height, Predicate<AABB> free) {
        if (face == Direction.DOWN) return null;
        if (face == Direction.UP) return hit;
        double x = hit.x + face.getStepX() * (width / 2 + GAP), z = hit.z + face.getStepZ() * (width / 2 + GAP);
        double y0 = hit.y - height / 2;
        double y = Hold.lift(y0, height, at -> free.test(box(x, at, z, width, height)));
        if (Double.isNaN(y)) return new Vec3(x, y0, z);
        double fell = 0;
        while (fell < DROP && free.test(box(x, y - fell - STEP, z, width, height))) fell += STEP;
        if (fell >= DROP) return null;
        double lo = fell, hi = fell + STEP;
        for (int i = 0; i < 8; i++) {
            double mid = (lo + hi) / 2;
            if (free.test(box(x, y - mid, z, width, height))) lo = mid;
            else hi = mid;
        }
        return new Vec3(x, y - lo, z);
    }

    static AABB box(double x, double feetY, double z, double width, double height) {
        return new AABB(x - width / 2, feetY, z - width / 2, x + width / 2, feetY + height, z + width / 2);
    }

    /** The box of {@code held} with its feet at {@code feet}. */
    public static AABB box(Entity held, Vec3 feet) {
        return held.getDimensions(held.getPose()).makeBoundingBox(feet);
    }

    /**
     * The spot for {@code held} under {@code wearer}'s crosshair ({@code eye} along {@code look}), valid within {@code reach} +
     * {@code slack} of the body; null for none.
     */
    public static @Nullable Spot spot(Level level, Player wearer, Entity held, Vec3 eye, Vec3 look, double reach, double slack) {
        Vec3 end = eye.add(look.normalize().scale(ActuatorReach.pickRange(reach)));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, wearer));
        if (hit.getType() != HitResult.Type.BLOCK || ShipFrames.get().isPlot(level, hit.getBlockPos())) return null;
        Predicate<AABB> free = b -> level.noCollision(held, b);
        Vec3 feet = feet(hit.getLocation(), hit.getDirection(), held.getBbWidth(), held.getBbHeight(), free);
        if (feet == null) return null;
        AABB box = box(held, feet);
        return new Spot(feet, box, valid(level, wearer, box, free, reach + slack));
    }

    /** {@code feet} as a spot for {@code held} (a client's), checked as {@link #spot} checks its own. */
    public static Spot check(Level level, Player wearer, Entity held, Vec3 feet, double reach) {
        AABB box = box(held, feet);
        return new Spot(feet, box, valid(level, wearer, box, b -> level.noCollision(held, b), reach));
    }

    private static boolean valid(Level level, Player wearer, AABB box, Predicate<AABB> free, double reach) {
        return ok(box, wearer.getBoundingBox(), Grips.body(wearer), reach, free) && sight(level, Grips.body(wearer), box, CollisionContext.of(wearer));
    }

    /** The geometric part of a valid spot: it fits, keeps clear of the wearer's box and comes within {@code reach} of the body. */
    public static boolean ok(AABB box, AABB wearer, Vec3 body, double reach, Predicate<AABB> free) {
        return Double.isFinite(box.minX + box.minY + box.minZ) && !box.intersects(wearer) && ActuatorReach.within(body, box, reach) && free.test(box);
    }

    /** The Grip sight rule toward a box: no block on the line from the body to its centre before the line enters it. */
    public static boolean sight(BlockGetter level, Vec3 body, AABB box, CollisionContext ctx) {
        Vec3 centre = box.getCenter();
        if (box.contains(body)) return true;
        Vec3 entry = box.clip(body, centre).orElse(body);
        BlockHitResult hit = level.clip(new ClipContext(body, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
        return hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(body) >= entry.distanceToSqr(body) - 1.0E-4;
    }
}
