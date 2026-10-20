package dev.tentaworks.rig;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.Pose;
import dev.tentaworks.compat.ShipFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Held check. Each of the four Tentacles owns one horizontal quadrant around the body; a quadrant has a Grip when some
 * exposed face ({@link #gripFace}, {@link #faceRect}) that looks toward the body lies in it within Reach. An outside edge within Reach (two faces of one
 * block on different axes, both looking toward the body: a pole seen from its corner, a wall's end, a ledge) counts as Held by
 * itself: its faces all sit on one side of the body planes, so the quadrants alone see one Grip there. Which Grip each Tentacle takes: {@link TentacleGait};
 * a Grip it takes or holds must also be {@link #visible} from the body, the Held count does not look that far.
 * Faces of unit-scale Ships count too: they are scanned and stored in the Ship's plot coords ({@link Face#frame()});
 * the body centre stays in world coords everywhere and is moved into a face's frame where needed.
 */
public final class Grips {
    public static final int SLOTS = 4;
    public static final int HELD = 2;
    /** A Grip point is sighted this far off its face, clear of the face's own block. */
    private static final double CLEARANCE = 0.05;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction.Axis[] AXES = Direction.Axis.values();
    /** Smallest face, both ways: keeps fence posts, chains, rails' sides and pane edges out. */
    private static final double MIN_FACE = 0.5;
    private static final double EPS = 1.0E-4;
    /** Widest seam a face is still covered across: stacked chests (2/16), a chest or honey block against a wall (1/16). */
    private static final double GAP = 0.2;
    private static final int ANY = 63;
    private static final short ALL_BITS = (short) (ANY | ANY << 6);
    private static final Sides NO_SIDES = new Sides(new AABB[6], 0);
    private static final Sides FULL_SIDES = sides(Shapes.block());
    /** {@link #sides(VoxelShape)} per shape instance: states and most block entities hand out the same shape every time. */
    private static final Cache<VoxelShape, Sides> SIDES = CacheBuilder.newBuilder().weakKeys().build();

    /** A face; {@code block} and {@code rect} are in {@code frame}'s plot coords, or world coords when it is null. */
    public record Face(BlockPos block, Direction dir, AABB rect, Frame frame) {
    }

    /** A block's faces in its unit cube by {@link Direction} ordinal, null where it has none; {@code solid}: one bit per face with no hole. */
    private record Sides(AABB[] rect, int solid) {
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

    static List<Face> scan(BlockGetter level, Vec3 centre, double radius) {
        List<Face> faces = new ArrayList<>();
        scan(level, centre, radius, null, faces);
        return faces;
    }

    /** {@code face} of the block at {@code b} is a Grip: it has a {@link #faceRect} and is not {@link #covered}. */
    static boolean gripFace(BlockGetter level, BlockPos b, Direction face) {
        AABB rect = faceRect(level, b, face);
        return rect != null && !covered(level, b, face, rect);
    }

    /**
     * A face is covered when it lies within {@link #GAP} of the block boundary and the neighbour's solid face toward it,
     * within that gap too, contains it: a full sturdy face covers anything, a half-high slab side covers the slab side next
     * to it, a thin cover such as a bottom slab covers the floor under it (its own top is the Grip then, see {@link #faceRect}),
     * a chest lid is covered by the chest on it. A face deeper in its block (a bottom slab's top) is never covered.
     */
    private static boolean covered(BlockGetter level, BlockPos b, Direction face, AABB rect) {
        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = positive ? rect.max(axis) : rect.min(axis);
        if (Math.abs(plane - (b.get(axis) + (positive ? 1 : 0))) > GAP) return false;
        BlockPos out = b.relative(face);
        Direction in = face.getOpposite();
        BlockState state = level.getBlockState(out);
        Sides sides = sides(level, out, state);
        AABB cover = sides.rect[in.ordinal()];
        if (cover == null) return false;
        if (state.isCollisionShapeFullBlock(level, out) || state.isFaceSturdy(level, out, in)) return true;
        if ((sides.solid & bit(in)) == 0) return false;
        cover = cover.move(out.getX(), out.getY(), out.getZ());
        if (Math.abs((positive ? cover.min(axis) : cover.max(axis)) - plane) > GAP) return false;
        for (Direction.Axis a : AXES)
            if (a != axis && (cover.min(a) > rect.min(a) + EPS || cover.max(a) < rect.max(a) - EPS)) return false;
        return true;
    }

    /** The Grip face {@code d} of the block at {@code b}, world coords (plot coords on a Ship), null when that side has none. */
    static AABB faceRect(BlockGetter level, BlockPos b, Direction d) {
        AABB unit = sides(level, b, level.getBlockState(b)).rect[d.ordinal()];
        return unit == null ? null : unit.move(b.getX(), b.getY(), b.getZ());
    }

    /**
     * {@link #sides(VoxelShape)} of the block's support shape; the full cube for a full collision block (leaves), none for a block
     * nothing collides with (hanging signs, one layer of snow).
     */
    private static Sides sides(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) return NO_SIDES;
        if (state.isCollisionShapeFullBlock(level, pos)) return FULL_SIDES;
        if (state.getCollisionShape(level, pos).isEmpty()) return NO_SIDES;
        VoxelShape shape = state.getBlockSupportShape(level, pos);
        Sides out = SIDES.getIfPresent(shape);
        if (out == null) {
            out = sides(shape);
            SIDES.put(shape, out);
        }
        return out;
    }

    /**
     * One face per side, in the unit cube, at the shape's outermost plane that way: the boxes on that plane as one rectangle
     * when they fill it (a bottom slab's top at half height, a chest's lid at 14/16, a stair's upper step), else the largest
     * of them alone (a stair's side is its lower step, a hopper's top ring and a scaffolding's legs are nothing). A face must
     * span {@link #MIN_FACE} both ways. The shape is cut to the unit cube first: walls and fences collide 1.5 high, a Grip must
     * stay in its block.
     */
    private static Sides sides(VoxelShape shape) {
        List<AABB> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            AABB cut = new AABB(Mth.clamp(box.minX, 0, 1), Mth.clamp(box.minY, 0, 1), Mth.clamp(box.minZ, 0, 1),
                    Mth.clamp(box.maxX, 0, 1), Mth.clamp(box.maxY, 0, 1), Mth.clamp(box.maxZ, 0, 1));
            if (cut.getXsize() > EPS && cut.getYsize() > EPS && cut.getZsize() > EPS) boxes.add(cut);
        }
        if (boxes.isEmpty()) return NO_SIDES;
        AABB[] out = new AABB[6];
        int solid = 0;
        double[] lo = new double[3], hi = new double[3];
        for (Direction d : DIRECTIONS) {
            Direction.Axis axis = d.getAxis();
            boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            double plane = positive ? -Double.MAX_VALUE : Double.MAX_VALUE;
            for (AABB box : boxes) plane = positive ? Math.max(plane, box.max(axis)) : Math.min(plane, box.min(axis));
            Arrays.fill(lo, Double.MAX_VALUE);
            Arrays.fill(hi, -Double.MAX_VALUE);
            double area = 0, bestArea = 0;
            AABB best = null;
            for (AABB box : boxes) {
                if (Math.abs((positive ? box.max(axis) : box.min(axis)) - plane) > EPS) continue;
                double boxArea = 1;
                for (Direction.Axis a : AXES) {
                    if (a == axis) continue;
                    lo[a.ordinal()] = Math.min(lo[a.ordinal()], box.min(a));
                    hi[a.ordinal()] = Math.max(hi[a.ordinal()], box.max(a));
                    boxArea *= box.max(a) - box.min(a);
                }
                area += boxArea;
                if (boxArea > bestArea) {
                    bestArea = boxArea;
                    best = box;
                }
            }
            lo[axis.ordinal()] = hi[axis.ordinal()] = plane;
            AABB rect = new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
            double rectArea = 1;
            for (Direction.Axis a : AXES) if (a != axis) rectArea *= hi[a.ordinal()] - lo[a.ordinal()];
            if (area >= rectArea - EPS) solid |= bit(d);
            else {
                lo[axis.ordinal()] = hi[axis.ordinal()] = plane;
                for (Direction.Axis a : AXES) {
                    if (a == axis) continue;
                    lo[a.ordinal()] = best.min(a);
                    hi[a.ordinal()] = best.max(a);
                }
                rect = new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
            }
            boolean wide = true;
            for (Direction.Axis a : AXES) if (a != axis && rect.max(a) - rect.min(a) < MIN_FACE - EPS) wide = false;
            if (wide) out[d.ordinal()] = rect;
        }
        return new Sides(out, solid);
    }

    private static void scan(BlockGetter level, Vec3 centre, double radius, Frame frame, List<Face> faces) {
        int r = Mth.ceil(radius) + 1;
        int n = 2 * r + 3;
        BlockPos origin = BlockPos.containing(centre).offset(-r - 1, -r - 1, -r - 1);
        short[] cells = new short[n * n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++)
                for (int z = 0; z < n; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    cells[(x * n + y) * n + z] = cell(level, pos);
                }
        double r2 = radius * radius;
        for (int x = 1; x < n - 1; x++)
            for (int y = 1; y < n - 1; y++)
                for (int z = 1; z < n - 1; z++) {
                    int own = cells[(x * n + y) * n + z];
                    if ((own & ANY) == 0) continue;
                    BlockPos b = origin.offset(x, y, z);
                    if (new AABB(b).distanceToSqr(centre) > r2) continue;
                    Sides sides = own == ALL_BITS ? FULL_SIDES : sides(level, b, level.getBlockState(b));
                    for (Direction d : DIRECTIONS) {
                        AABB unit = sides.rect[d.ordinal()];
                        if (unit == null) continue;
                        Direction.Axis axis = d.getAxis();
                        double gap = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - unit.max(axis) : unit.min(axis);
                        AABB rect = null;
                        if (gap <= GAP) {
                            int out = cells[((x + d.getStepX()) * n + y + d.getStepY()) * n + z + d.getStepZ()];
                            if (gap < EPS && (out & full(d.getOpposite())) != 0) continue;
                            if ((out & bit(d.getOpposite())) != 0) {
                                rect = unit.move(b.getX(), b.getY(), b.getZ());
                                if (covered(level, b, d, rect)) continue;
                            }
                        }
                        faces.add(new Face(b, d, rect == null ? unit.move(b.getX(), b.getY(), b.getZ()) : rect, frame));
                    }
                }
    }

    /** Bits 0-5: the block has a face that way ({@link #faceRect}); bits 6-11: that face is whole and sturdy (a full collision block, or vanilla's attach rule). 0 for air. */
    private static short cell(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return 0;
        if (state.isCollisionShapeFullBlock(level, pos)) return ALL_BITS;
        Sides sides = sides(level, pos, state);
        int bits = 0;
        for (Direction d : DIRECTIONS) {
            if (sides.rect[d.ordinal()] == null) continue;
            bits |= bit(d);
            if (state.isFaceSturdy(level, pos, d)) bits |= full(d);
        }
        return (short) bits;
    }

    private static int bit(Direction d) {
        return 1 << d.ordinal();
    }

    private static int full(Direction d) {
        return 1 << 6 + d.ordinal();
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
        return held >= HELD || !edge(faces, view, r2) ? held : HELD;
    }

    /** Faces of one block are consecutive in a scan: two of them on different axes, both within Reach and facing the body. */
    private static boolean edge(List<Face> faces, View view, double r2) {
        BlockPos block = null;
        Frame frame = null;
        int axes = 0;
        for (Face f : faces) {
            if (!f.block.equals(block) || f.frame != frame) {
                block = f.block;
                frame = f.frame;
                axes = 0;
            }
            if (view.dist2(f) > r2) continue;
            axes |= 1 << f.dir.getAxis().ordinal();
            if (Integer.bitCount(axes) >= 2) return true;
        }
        return false;
    }

    public static boolean held(List<Face> faces, Vec3 centre, double reach) {
        return count(faces, centre, reach) >= HELD;
    }

    /**
     * Largest part of {@code motion} that keeps the body Held. Tries the full move, then the move with one axis dropped
     * (slides along gap edges and down cliff walls), then the vertical part alone (a body at full stretch from a pole keeps
     * climbing while the Heading points away from it), each shortened by bisection; keeps the one that gets furthest along the heading.
     * {@code faces} must cover Reach + |motion| + 0.5 around the body.
     */
    public static Vec3 clampHeld(Level level, Entity entity, Vec3 motion, double reach, List<Face> faces) {
        return clampHeld(level, entity, motion, reach, faces, Vec3.ZERO);
    }

    /**
     * As {@link #clampHeld(Level, Entity, Vec3, double, List)} for a body the carry Ship moves by {@code carry} this tick
     * (world coords, before the Rig's own move): Held is tested where the body really ends up. {@code faces} must also cover the carry.
     */
    public static Vec3 clampHeld(Level level, Entity entity, Vec3 motion, double reach, List<Face> faces, Vec3 carry) {
        if (motion.lengthSqr() < 1.0E-8) return Vec3.ZERO;
        Vec3 centre = body(entity).add(carry);
        Vec3 dir = motion.normalize();
        Vec3[] tries = {motion, new Vec3(0, motion.y, motion.z), new Vec3(motion.x, 0, motion.z), new Vec3(motion.x, motion.y, 0), new Vec3(0, motion.y, 0)};
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
     * A Grip is real when its point lies in its block and within Reach of the body, the block is loaded and has that face
     * ({@link #faceRect}) under the point, its outer neighbour is loaded and does not {@link #covered cover} it, and the point is
     * {@link #visible} from the body. The point checks come first so a forged Grip can never make the server read an unloaded position.
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
        if (!new AABB(b).inflate(0.02).contains(p)) return false;
        if (centres.stream().noneMatch(c -> p.distanceToSqr(c) <= r2)) return false;
        BlockPos out = b.relative(face);
        boolean outLoaded = level.isLoaded(out);
        if (!level.isLoaded(b) || (!outLoaded && !plot)) return false;
        AABB rect = faceRect(level, b, face);
        if (rect == null || !rect.inflate(0.02).contains(p)) return false;
        if (outLoaded && covered(level, b, face, rect)) return false;
        for (Vec3 c : centres) if (p.distanceToSqr(c) <= r2 && visible(level, c, p, face, plot)) return true;
        return false;
    }

    /**
     * The body can see the Grip: the straight line from the body centre to the point, lifted {@link #CLEARANCE} off its face,
     * crosses no collision shape. A cave under the floor, the far side of a wall, a ledge seen from below are no Grips, the
     * Tentacle would pass through a block. Traces blocks of {@code level} only, never through {@code Level.clip} (a Ship hit
     * would come back in plot coords); a Ship Grip is traced in plot coords with the body centre moved there. Unloaded counts as
     * solid in the world and as empty in plot space. The line stops a hair short so a point on an edge does not touch the block beside it.
     * Only the Grip's own frame is traced: a Ship hull between the body and a world Grip, or world blocks before a Ship Grip, are not seen.
     */
    static boolean visible(BlockGetter level, Vec3 centre, Vec3 point, Direction face, boolean plot) {
        Vec3 off = point.add(face.getStepX() * CLEARANCE, face.getStepY() * CLEARANCE, face.getStepZ() * CLEARANCE);
        Vec3 from = centre, to = centre.add(off.subtract(centre).scale(1 - 1.0E-4));
        Boolean hit = BlockGetter.traverseBlocks(from, to, (Void) null, (c, pos) -> {
            if (level instanceof Level l && !l.isLoaded(pos)) return plot ? null : Boolean.TRUE;
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) return null;
            VoxelShape shape = s.getCollisionShape(level, pos);
            return shape.isEmpty() || shape.clip(from, to, pos) == null ? null : Boolean.TRUE;
        }, c -> null);
        return hit == null;
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
            enter(f.frame);
            AABB r = f.rect;
            int step = f.dir.getAxisDirection().getStep();
            return QuadrantClip.dist2(f.dir.getAxis().ordinal(), step, new double[]{r.minX, r.minY, r.minZ},
                    new double[]{r.maxX, r.maxY, r.maxZ}, local, sx(q), sz(q));
        }

        /** Distance to the whole face (no quadrant), {@link Double#MAX_VALUE} when the body is behind it. */
        double dist2(Face f) {
            if (f.frame == null) return rectDist2(f, centre.x, centre.y, centre.z);
            enter(f.frame);
            return rectDist2(f, local[0], local[1], local[2]);
        }

        private void enter(Frame to) {
            if (to == frame) return;
            frame = to;
            put(0, frame.toLocal(centre));
            put(3, frame.normalToLocal(X));
            put(6, frame.normalToLocal(Z));
        }

        private void put(int at, Vec3 v) {
            local[at] = v.x;
            local[at + 1] = v.y;
            local[at + 2] = v.z;
        }
    }

    private static double worldDist2(Face f, Vec3 c, int q) {
        AABB r = f.rect;
        if (behind(f, c.get(f.dir.getAxis()))) return Double.MAX_VALUE;
        double minX = r.minX, maxX = r.maxX, minZ = r.minZ, maxZ = r.maxZ;
        if (sx(q) > 0) minX = Math.max(minX, c.x);
        else maxX = Math.min(maxX, c.x);
        if (sz(q) > 0) minZ = Math.max(minZ, c.z);
        else maxZ = Math.min(maxZ, c.z);
        if (minX > maxX || minZ > maxZ) return Double.MAX_VALUE;
        double dx = c.x - Mth.clamp(c.x, minX, maxX), dy = c.y - Mth.clamp(c.y, r.minY, r.maxY), dz = c.z - Mth.clamp(c.z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@code x, y, z}: the body centre in the face's frame. */
    private static double rectDist2(Face f, double x, double y, double z) {
        AABB r = f.rect;
        if (behind(f, f.dir.getAxis().choose(x, y, z))) return Double.MAX_VALUE;
        double dx = x - Mth.clamp(x, r.minX, r.maxX), dy = y - Mth.clamp(y, r.minY, r.maxY), dz = z - Mth.clamp(z, r.minZ, r.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean behind(Face f, double along) {
        Direction.Axis axis = f.dir.getAxis();
        double plane = f.dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? f.rect.max(axis) : f.rect.min(axis);
        return (along - plane) * f.dir.getAxisDirection().getStep() <= 0;
    }

    private static int sx(int q) {
        return (q & 1) == 0 ? 1 : -1;
    }

    private static int sz(int q) {
        return (q & 2) == 0 ? 1 : -1;
    }
}
