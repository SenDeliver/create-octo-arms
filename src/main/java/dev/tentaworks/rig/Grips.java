package dev.tentaworks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Held check. Each of the four Tentacles owns one horizontal quadrant around the body; a quadrant has a Grip when some
 * exposed full-block face that looks toward the body lies in it within Reach. Which Grip each Tentacle takes: {@link TentacleGait}.
 */
public final class Grips {
    public static final int SLOTS = 4;
    public static final int HELD = 2;
    private static final double THROUGH = 0.3;

    public record Face(BlockPos block, Direction dir, AABB rect) {
    }

    private Grips() {
    }

    public static Vec3 body(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    public static List<Face> faces(BlockGetter level, Vec3 centre, double radius) {
        int r = Mth.ceil(radius) + 1;
        int n = 2 * r + 3;
        BlockPos origin = BlockPos.containing(centre).offset(-r - 1, -r - 1, -r - 1);
        boolean[] full = new boolean[n * n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++)
                for (int z = 0; z < n; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    full[(x * n + y) * n + z] = level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
                }
        double r2 = radius * radius;
        List<Face> faces = new ArrayList<>();
        for (int x = 1; x < n - 1; x++)
            for (int y = 1; y < n - 1; y++)
                for (int z = 1; z < n - 1; z++) {
                    if (!full[(x * n + y) * n + z]) continue;
                    BlockPos b = origin.offset(x, y, z);
                    if (new AABB(b).distanceToSqr(centre) > r2) continue;
                    for (Direction d : Direction.values()) {
                        if (full[((x + d.getStepX()) * n + y + d.getStepY()) * n + z + d.getStepZ()]) continue;
                        faces.add(new Face(b, d, faceRect(b, d)));
                    }
                }
        return faces;
    }

    public static int count(List<Face> faces, Vec3 centre, double reach) {
        double r2 = reach * reach;
        int held = 0;
        for (int q = 0; q < SLOTS; q++) {
            for (Face f : faces) {
                if (quadrantDist2(f, centre, q) <= r2) {
                    held++;
                    break;
                }
            }
        }
        return held;
    }

    public static boolean held(List<Face> faces, Vec3 centre, double reach) {
        return count(faces, centre, reach) >= HELD;
    }

    /**
     * Largest part of {@code motion} that keeps the body Held. Tries the full move, then the move with one axis dropped
     * (slides along gap edges and down cliff walls), each shortened by bisection; keeps the one that gets furthest along the heading.
     * {@code faces} must cover Reach + |motion| + 0.5 around the body.
     */
    public static Vec3 clampHeld(Level level, Entity entity, Vec3 motion, double reach, List<Face> faces) {
        if (motion.lengthSqr() < 1.0E-8) return Vec3.ZERO;
        Vec3 centre = body(entity);
        Vec3 dir = motion.normalize();
        Vec3[] tries = {motion, new Vec3(0, motion.y, motion.z), new Vec3(motion.x, 0, motion.z), new Vec3(motion.x, motion.y, 0)};
        Vec3 best = Vec3.ZERO;
        double bestProgress = 1.0E-4;
        for (Vec3 m : tries) {
            if (m.lengthSqr() < 1.0E-8) continue;
            Vec3 moved = heldPart(level, entity, faces, centre, m, reach);
            double progress = moved.dot(dir);
            if (progress > bestProgress + 1.0E-6) {
                best = moved;
                bestProgress = progress;
            }
            if (m == motion && moved.distanceToSqr(motion) < 1.0E-10) return motion;
        }
        return best;
    }

    /**
     * First point along this tick's fall step where the body is Held, so a fast fall cannot step over a thin Held band.
     */
    public static Vec3 firstHeld(Level level, Entity entity, Vec3 motion, double reach) {
        double len = motion.length();
        if (len < 1.0E-4) return null;
        Vec3 centre = body(entity);
        List<Face> faces = faces(level, centre.add(motion.scale(0.5)), reach + len / 2 + 0.5);
        int n = Mth.ceil(len / 0.1);
        for (int k = 1; k <= n; k++) {
            Vec3 moved = collide(level, entity, motion.scale((double) k / n));
            if (held(faces, centre.add(moved), reach)) return moved;
        }
        return null;
    }

    private static Vec3 heldPart(Level level, Entity entity, List<Face> faces, Vec3 centre, Vec3 m, double reach) {
        if (held(faces, centre.add(collide(level, entity, m)), reach)) return m;
        double lo = 0, hi = 1;
        for (int i = 0; i < 8; i++) {
            double mid = (lo + hi) / 2;
            if (held(faces, centre.add(collide(level, entity, m.scale(mid))), reach)) lo = mid;
            else hi = mid;
        }
        return m.scale(lo);
    }

    /** The part of {@code m} the entity's collision box really moves this tick. */
    public static Vec3 collide(Level level, Entity entity, Vec3 m) {
        return Entity.collideBoundingBox(entity, m, entity.getBoundingBox(), level, List.of());
    }

    /**
     * A Grip is real when its point lies on the named face, within Reach of the body, on a loaded full block whose outer
     * neighbour is loaded and not full, and the body is not behind that face with solid blocks in between. The point check
     * comes first so a forged Grip can never make the server read an unloaded position.
     */
    public static boolean valid(Level level, Grip grip, Vec3 centre, double reach) {
        BlockPos b = grip.block();
        Direction face = grip.face();
        if (!faceRect(b, face).inflate(0.02).contains(grip.point())) return false;
        if (grip.point().distanceToSqr(centre) > reach * reach) return false;
        BlockPos out = b.relative(face);
        if (!level.isLoaded(b) || !level.isLoaded(out)) return false;
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? b.get(face.getAxis()) + 1 : b.get(face.getAxis());
        if (throughBlock(level, face, plane, centre)) return false;
        return level.getBlockState(b).isCollisionShapeFullBlock(level, b) && !level.getBlockState(out).isCollisionShapeFullBlock(level, out);
    }

    /** The body sits behind the face's plane with a solid block between: the Tentacle would have to pass through the wall. */
    private static boolean throughBlock(Level level, Direction face, double plane, Vec3 centre) {
        Direction.Axis axis = face.getAxis();
        int step = face.getAxisDirection().getStep();
        if ((centre.get(axis) - plane) * step >= -THROUGH) return false;
        double inside = plane - step * 0.5;
        BlockPos p = BlockPos.containing(axis == Direction.Axis.X ? inside : centre.x, axis == Direction.Axis.Y ? inside : centre.y, axis == Direction.Axis.Z ? inside : centre.z);
        return !level.isLoaded(p) || level.getBlockState(p).isCollisionShapeFullBlock(level, p);
    }

    private static AABB faceRect(BlockPos b, Direction d) {
        double x0 = b.getX(), y0 = b.getY(), z0 = b.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        return switch (d) {
            case DOWN -> new AABB(x0, y0, z0, x1, y0, z1);
            case UP -> new AABB(x0, y1, z0, x1, y1, z1);
            case NORTH -> new AABB(x0, y0, z0, x1, y1, z0);
            case SOUTH -> new AABB(x0, y0, z1, x1, y1, z1);
            case WEST -> new AABB(x0, y0, z0, x0, y1, z1);
            case EAST -> new AABB(x1, y0, z0, x1, y1, z1);
        };
    }

    private static double quadrantDist2(Face f, Vec3 c, int q) {
        AABB r = f.rect;
        Direction.Axis axis = f.dir.getAxis();
        double plane = f.dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? r.max(axis) : r.min(axis);
        if ((c.get(axis) - plane) * f.dir.getAxisDirection().getStep() <= 0) return Double.MAX_VALUE;
        double minX = r.minX, maxX = r.maxX, minZ = r.minZ, maxZ = r.maxZ;
        if (sx(q) > 0) minX = Math.max(minX, c.x);
        else maxX = Math.min(maxX, c.x);
        if (sz(q) > 0) minZ = Math.max(minZ, c.z);
        else maxZ = Math.min(maxZ, c.z);
        if (minX > maxX || minZ > maxZ) return Double.MAX_VALUE;
        double dx = c.x - Mth.clamp(c.x, minX, maxX), dy = c.y - Mth.clamp(c.y, r.minY, r.maxY), dz = c.z - Mth.clamp(c.z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int sx(int q) {
        return (q & 1) == 0 ? 1 : -1;
    }

    private static int sz(int q) {
        return (q & 2) == 0 ? 1 : -1;
    }
}
