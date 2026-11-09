package dev.tentaworks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Far Use past the vanilla interaction range must not pass through walls. Vanilla checks only the distance, and Far Use lifts it
 * to the Reach, so a target farther than the player's own range (plus vanilla's 1 block of slack) must also be in sight: no
 * collision shape on the line from the eye or the body to it. Within the player's own range vanilla rules alone apply.
 */
public final class WorkSight {
    private static final double INSET = 0.01;

    private WorkSight() {
    }

    /** A target {@code distSqr} away (squared, from the eye to its box) is past {@code base}, the range without the Far Use lift. */
    public static boolean needsSight(double distSqr, double base) {
        double r = base + 1.0;
        return distSqr > r * r;
    }

    /**
     * The block at {@code pos} is in sight of one of {@code origins}: the line to {@code point} (the clicked spot, or null), to the
     * nearest point of the block, or to its centre crosses no collision shape of another block.
     */
    public static boolean seesBlock(BlockGetter level, BlockPos pos, Vec3 point, Vec3... origins) {
        AABB in = new AABB(pos).deflate(INSET);
        Vec3 centre = in.getCenter();
        for (Vec3 o : origins) {
            if (point != null && clear(level, pos, o, inside(in, point))) return true;
            Vec3 near = new Vec3(Mth.clamp(o.x, in.minX, in.maxX), Mth.clamp(o.y, in.minY, in.maxY), Mth.clamp(o.z, in.minZ, in.maxZ));
            if (clear(level, pos, o, near) || clear(level, pos, o, centre)) return true;
        }
        return false;
    }

    /** {@code box} (an entity's) is in sight of one of {@code origins}: no block on the line to its centre before the line enters it. */
    public static boolean seesBox(Level level, Entity source, AABB box, Vec3... origins) {
        Vec3 centre = box.getCenter();
        for (Vec3 o : origins) {
            Vec3 entry = box.clip(o, centre).orElse(o);
            BlockHitResult hit = level.clip(new ClipContext(o, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(o) >= entry.distanceToSqr(o) - 1.0E-4) return true;
        }
        return false;
    }

    private static Vec3 inside(AABB in, Vec3 p) {
        return new Vec3(Mth.clamp(p.x, in.minX, in.maxX), Mth.clamp(p.y, in.minY, in.maxY), Mth.clamp(p.z, in.minZ, in.maxZ));
    }

    private static boolean clear(BlockGetter level, BlockPos target, Vec3 from, Vec3 to) {
        Boolean hit = BlockGetter.traverseBlocks(from, to, (Void) null, (c, p) -> {
            if (p.equals(target)) return null;
            if (level instanceof Level l && !l.isLoaded(p)) return Boolean.TRUE;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) return null;
            VoxelShape shape = s.getCollisionShape(level, p);
            return shape.isEmpty() || shape.clip(from, to, p) == null ? null : Boolean.TRUE;
        }, c -> null);
        return hit == null;
    }
}
