package dev.tentaworks.rig;

import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.Pose;
import dev.tentaworks.compat.ShipFrames;
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
 * Faces of unit-scale Ships count too: they are scanned and stored in the Ship's plot coords ({@link Face#frame()});
 * the body centre stays in world coords everywhere and is moved into a face's frame where needed.
 */
public final class Grips {
    public static final int SLOTS = 4;
    public static final int HELD = 2;
    private static final double THROUGH = 0.3;

    /** A face; {@code block} and {@code rect} are in {@code frame}'s plot coords, or world coords when it is null. */
    public record Face(BlockPos block, Direction dir, AABB rect, Frame frame) {
    }

    private Grips() {
    }

    public static Vec3 body(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    public static List<Face> faces(Level level, Vec3 centre, double radius) {
        List<Face> faces = new ArrayList<>();
        scan(level, centre, radius, null, faces);
        for (Frame frame : ShipFrames.get().near(level, new AABB(centre, centre).inflate(radius + 1)))
            if (frame.unitScale()) scan(level, frame.toLocal(centre), radius, frame, faces);
        return faces;
    }

    private static void scan(BlockGetter level, Vec3 centre, double radius, Frame frame, List<Face> faces) {
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
        for (int x = 1; x < n - 1; x++)
            for (int y = 1; y < n - 1; y++)
                for (int z = 1; z < n - 1; z++) {
                    if (!full[(x * n + y) * n + z]) continue;
                    BlockPos b = origin.offset(x, y, z);
                    if (new AABB(b).distanceToSqr(centre) > r2) continue;
                    for (Direction d : Direction.values()) {
                        if (full[((x + d.getStepX()) * n + y + d.getStepY()) * n + z + d.getStepZ()]) continue;
                        faces.add(new Face(b, d, faceRect(b, d), frame));
                    }
                }
    }

    public static int count(List<Face> faces, Vec3 centre, double reach) {
        double r2 = reach * reach;
        int held = 0;
        View view = new View(centre);
        for (int q = 0; q < SLOTS; q++) {
            for (Face f : faces) {
                if (view.quadrantDist2(f, q) <= r2) {
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
     * A Grip in plot coords needs a live unit-scale Ship there (Ships on in the config) and passes when it is real under any
     * of the Ship's {@link ShipFrames#recent recent} poses: the client built it from a pose that lags the server's.
     */
    public static boolean valid(Level level, Grip grip, Vec3 centre, double reach) {
        ShipFrames ships = ShipFrames.get();
        if (!ships.isPlot(level, grip.block())) return validIn(level, grip, List.of(centre), reach, false);
        Frame frame = ships.containing(level, grip.block());
        if (frame == null || !frame.unitScale()) return false;
        List<Pose> poses = ships.recent(frame);
        List<Vec3> centres = new ArrayList<>(poses.size());
        for (Pose pose : poses) centres.add(pose.toLocal(centre));
        return validIn(level, grip, centres, reach, true);
    }

    /**
     * {@code centres}: the body centre in the Grip's frame, one per candidate pose. Unloaded counts as solid in the world
     * (it may be a wall) but as empty in plot space, where it is just space no Ship owns.
     */
    private static boolean validIn(Level level, Grip grip, List<Vec3> centres, double reach, boolean plot) {
        BlockPos b = grip.block();
        Direction face = grip.face();
        Vec3 p = grip.point();
        double r2 = reach * reach;
        if (!faceRect(b, face).inflate(0.02).contains(p)) return false;
        if (centres.stream().noneMatch(c -> p.distanceToSqr(c) <= r2)) return false;
        BlockPos out = b.relative(face);
        boolean outLoaded = level.isLoaded(out);
        if (!level.isLoaded(b) || (!outLoaded && !plot)) return false;
        if (!level.getBlockState(b).isCollisionShapeFullBlock(level, b) || (outLoaded && level.getBlockState(out).isCollisionShapeFullBlock(level, out))) return false;
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? b.get(face.getAxis()) + 1 : b.get(face.getAxis());
        for (Vec3 c : centres) if (p.distanceToSqr(c) <= r2 && !throughBlock(level, face, plane, c, plot)) return true;
        return false;
    }

    /** The body sits behind the face's plane with a solid block between: the Tentacle would have to pass through the wall. */
    private static boolean throughBlock(Level level, Direction face, double plane, Vec3 centre, boolean plot) {
        Direction.Axis axis = face.getAxis();
        int step = face.getAxisDirection().getStep();
        if ((centre.get(axis) - plane) * step >= -THROUGH) return false;
        double inside = plane - step * 0.5;
        BlockPos p = BlockPos.containing(axis == Direction.Axis.X ? inside : centre.x, axis == Direction.Axis.Y ? inside : centre.y, axis == Direction.Axis.Z ? inside : centre.z);
        return level.isLoaded(p) ? level.getBlockState(p).isCollisionShapeFullBlock(level, p) : !plot;
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

    /** The body centre as seen from each face's frame, moved into a frame once per run of faces from it. */
    private static final class View {
        private static final Vec3 X = new Vec3(1, 0, 0);
        private static final Vec3 Z = new Vec3(0, 0, 1);
        private final Vec3 centre;
        private final double[] local = new double[9];
        private Frame frame;

        View(Vec3 centre) {
            this.centre = centre;
        }

        double quadrantDist2(Face f, int q) {
            if (f.frame == null) return worldDist2(f, centre, q);
            if (f.frame != frame) {
                frame = f.frame;
                put(0, frame.toLocal(centre));
                put(3, frame.normalToLocal(X));
                put(6, frame.normalToLocal(Z));
            }
            AABB r = f.rect;
            int step = f.dir.getAxisDirection().getStep();
            return QuadrantClip.dist2(f.dir.getAxis().ordinal(), step, new double[]{r.minX, r.minY, r.minZ},
                    new double[]{r.maxX, r.maxY, r.maxZ}, local, sx(q), sz(q));
        }

        private void put(int at, Vec3 v) {
            local[at] = v.x;
            local[at + 1] = v.y;
            local[at + 2] = v.z;
        }
    }

    private static double worldDist2(Face f, Vec3 c, int q) {
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
