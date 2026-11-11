package dev.tentaworks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Far Use past arm's length must not pass through walls. The server picks again as the client did: from the eye along the
 * look, as far as the lifted range. The target is in sight when that pick finds it first. The client acts with this tick's
 * look and the server still has the last tick's, so a pick that finds another block (or nothing) falls back to a line from
 * the eye to a visible point of the target (the clicked spot, the clicked face, the nearest point): in sight when no
 * collision shape of another block crosses it.
 */
public final class WorkSight {
    private static final double INSET = 0.01;
    private static final double SLACK = 1.0;
    private static final double EPS = 1.0E-3;

    private WorkSight() {
    }

    /** The block at {@code pos} ({@code face} the clicked face and {@code point} the clicked spot, each null when unknown) is in sight of {@code player}. */
    public static boolean seesBlock(Player player, BlockPos pos, @Nullable Direction face, @Nullable Vec3 point) {
        return seesBlock(player.level(), pos, face, point, player.getEyePosition(), player.getViewVector(1.0F), player.blockInteractionRange() + SLACK,
                CollisionContext.of(player));
    }

    /** {@code target} is in sight of {@code player}. */
    public static boolean seesEntity(Player player, Entity target) {
        AABB box = target.getBoundingBox().inflate(target.getPickRadius());
        return seesBox(player.level(), box, player.getEyePosition(), player.getViewVector(1.0F), player.entityInteractionRange() + SLACK,
                CollisionContext.of(player));
    }

    /** {@link #seesBlock(Player, BlockPos, Direction, Vec3)} from {@code eye} looking along {@code look} as far as {@code range}. */
    public static boolean seesBlock(BlockGetter level, BlockPos pos, @Nullable Direction face, @Nullable Vec3 point, Vec3 eye, Vec3 look, double range,
                                    CollisionContext ctx) {
        BlockHitResult pick = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ctx));
        if (picked(level, pos, pick, point)) return true;
        for (Vec3 to : spots(level, pos, face, point, eye)) {
            if (clear(level, pos, eye, to)) return true;
        }
        return false;
    }

    /**
     * The pick found {@code pos}, or the block in front of the face it hit while the clicked spot lies on {@code pos}'s own
     * shape (a thin block on top of the hit one, seen one tick later).
     */
    static boolean picked(BlockGetter level, BlockPos pos, BlockHitResult pick, @Nullable Vec3 point) {
        if (pick.getType() != HitResult.Type.BLOCK) return false;
        if (pick.getBlockPos().equals(pos)) return true;
        if (point == null || !pick.getBlockPos().relative(pick.getDirection()).equals(pos)) return false;
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        for (AABB b : shape.toAabbs()) {
            if (b.move(pos).inflate(EPS).contains(point)) return true;
        }
        return false;
    }

    /** {@code box} (an entity's, pick radius included) is in sight: the look ray meets it before any block, or a line to its centre crosses no block. */
    public static boolean seesBox(BlockGetter level, AABB box, Vec3 eye, Vec3 look, double range, CollisionContext ctx) {
        if (box.contains(eye)) return true;
        Vec3 end = eye.add(look.scale(range));
        Optional<Vec3> on = box.clip(eye, end);
        if (on.isPresent()) {
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ctx));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= on.get().distanceToSqr(eye)) return true;
        }
        Vec3 centre = box.getCenter();
        Vec3 entry = box.clip(eye, centre).orElse(eye);
        BlockHitResult hit = level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= entry.distanceToSqr(eye) - 1.0E-4;
    }

    /** Visible points of the block to draw a line to, each stopped just short of the block so the line never dips into a neighbour. */
    public static List<Vec3> spots(BlockGetter level, BlockPos pos, @Nullable Direction face, @Nullable Vec3 point, Vec3 eye) {
        List<Vec3> out = new ArrayList<>();
        if (point != null) out.add(point);
        Vec3 centre = Vec3.atCenterOf(pos);
        if (face != null) out.add(centre.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)));
        for (Direction d : Direction.values()) {
            if (d == face) continue;
            Vec3 n = Vec3.atLowerCornerOf(d.getNormal());
            Vec3 c = centre.add(n.scale(0.5));
            if (eye.subtract(c).dot(n) > 0) out.add(c);
        }
        AABB box = new AABB(pos);
        out.add(new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ)));
        List<Vec3> spots = new ArrayList<>(out.size());
        for (Vec3 p : out) {
            Vec3 back = eye.subtract(p);
            double len = back.length();
            spots.add(len <= INSET ? eye : p.add(back.scale(INSET / len)));
        }
        return spots;
    }

    private static boolean clear(BlockGetter level, BlockPos target, Vec3 from, Vec3 to) {
        return blocker(level, target, from, to) == null;
    }

    /** The first block other than {@code target} whose collision shape the line crosses (an unloaded one counts), null when none. */
    public static @Nullable BlockPos blocker(BlockGetter level, BlockPos target, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, (Void) null, (c, p) -> {
            if (p.equals(target)) return null;
            if (level instanceof Level l && !l.isLoaded(p)) return p.immutable();
            BlockState s = level.getBlockState(p);
            if (s.isAir()) return null;
            VoxelShape shape = s.getCollisionShape(level, p);
            return shape.isEmpty() || shape.clip(from, to, p) == null ? null : p.immutable();
        }, c -> null);
    }
}
