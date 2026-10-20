package dev.tentaworks.rig;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which block faces are Grips (issue #19 and the slab report after it). A block's faces come from its support shape: on each
 * side, the part of the shape on its outermost plane is the face, at that plane (a bottom slab's top sits at half height),
 * when it is at least half a block wide both ways. A face on the block boundary is covered when the neighbour's face toward
 * it contains it. Create's Fluid Tank is the regression case of #19: its collision shape for the empty context (what
 * {@code isCollisionShapeFullBlock} caches) is cut down for campfire smoke, but its support shape is a full cube.
 */
class GripFaceTest {
    private static final Vec3 CENTRE = new Vec3(0.5, 2.5, 0.5);
    private static final double RADIUS = 4;
    private static final double EPS = 1.0E-6;
    private static BlockState tank;

    /** The test task runs under FML (build.gradle {@code unitTest}): registries are up, Create is a loaded mod. */
    @BeforeAll
    static void setUp() {
        tank = AllBlocks.FLUID_TANK.getDefaultState();
    }

    /** A handful of blocks in air. */
    static final class World implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        World put(int x, int y, int z, BlockState state) {
            blocks.put(new BlockPos(x, y, z), state);
            return this;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState stone() {
        return Blocks.STONE.defaultBlockState();
    }

    private static boolean has(List<Grips.Face> faces, int x, int y, int z, Direction d) {
        return rect(faces, x, y, z, d) != null;
    }

    private static AABB rect(List<Grips.Face> faces, int x, int y, int z, Direction d) {
        BlockPos b = new BlockPos(x, y, z);
        return faces.stream().filter(f -> f.block().equals(b) && f.dir() == d).map(Grips.Face::rect).findFirst().orElse(null);
    }

    private static long count(List<Grips.Face> faces, int x, int y, int z) {
        BlockPos b = new BlockPos(x, y, z);
        return faces.stream().filter(f -> f.block().equals(b)).count();
    }

    private static void assertRect(AABB expected, AABB actual, String what) {
        assertNotNull(actual, what);
        assertTrue(Math.abs(expected.minX - actual.minX) < EPS && Math.abs(expected.minY - actual.minY) < EPS && Math.abs(expected.minZ - actual.minZ) < EPS
                && Math.abs(expected.maxX - actual.maxX) < EPS && Math.abs(expected.maxY - actual.maxY) < EPS && Math.abs(expected.maxZ - actual.maxZ) < EPS,
                what + ": expected " + expected + " got " + actual);
    }

    @Test
    void tankIsNotAFullCollisionBlockButItsFacesAreSturdy() {
        World w = new World().put(0, 0, 0, tank);
        BlockPos p = BlockPos.ZERO;
        assertFalse(tank.isCollisionShapeFullBlock(w, p), "the premise of the bug");
        for (Direction d : Direction.values()) assertTrue(tank.isFaceSturdy(w, p, d), d.toString());
    }

    @Test
    void loneTankHasSixGripFaces() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, tank), CENTRE, RADIUS);
        assertEquals(6, count(faces, 0, 0, 0));
        assertRect(new AABB(0, 1, 0, 1, 1, 1), rect(faces, 0, 0, 0, Direction.UP), "tank top");
    }

    @Test
    void tankSeamIsNoGrip() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, tank).put(1, 0, 0, tank), CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.EAST));
        assertFalse(has(faces, 1, 0, 0, Direction.WEST));
        assertEquals(5, count(faces, 0, 0, 0));
        assertEquals(5, count(faces, 1, 0, 0));
    }

    @Test
    void tankOnStoneCoversTheStoneTop() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, tank), CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.UP));
        assertFalse(has(faces, 0, 1, 0, Direction.DOWN));
        assertTrue(has(faces, 0, 1, 0, Direction.UP));
    }

    @Test
    void loneStoneStillHasSixFaces() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()), CENTRE, RADIUS);
        assertEquals(6, count(faces, 0, 0, 0));
    }

    /** The owner's report: a Tentacle could not take the top of the slab under the body. */
    @Test
    void bottomSlabGripsByItsTopAtHalfHeight() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 4, 0, slab(SlabType.BOTTOM)), CENTRE, RADIUS);
        assertRect(new AABB(0, 4.5, 0, 1, 4.5, 1), rect(faces, 0, 4, 0, Direction.UP), "slab top");
        assertRect(new AABB(0, 4, 0, 1, 4, 1), rect(faces, 0, 4, 0, Direction.DOWN), "slab underside");
        assertRect(new AABB(1, 4, 0, 1, 4.5, 1), rect(faces, 0, 4, 0, Direction.EAST), "slab side, half high");
        assertEquals(6, count(faces, 0, 4, 0), faces.toString());
    }

    @Test
    void topSlabGripsByItsUndersideAtHalfHeight() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, slab(SlabType.TOP)), CENTRE, RADIUS);
        assertRect(new AABB(0, 0.5, 0, 1, 0.5, 1), rect(faces, 0, 0, 0, Direction.DOWN), "slab underside");
        assertRect(new AABB(0, 1, 0, 1, 1, 1), rect(faces, 0, 0, 0, Direction.UP), "slab top");
        assertRect(new AABB(0, 0.5, 0, 0, 1, 1), rect(faces, 0, 0, 0, Direction.WEST), "slab side");
        assertEquals(6, count(faces, 0, 0, 0), faces.toString());
    }

    /** Two slabs side by side: the seam between them is no Grip (a half-high face covers a half-high face). */
    @Test
    void slabRowHasNoSeamFaces() {
        World w = new World().put(0, 0, 0, slab(SlabType.BOTTOM)).put(1, 0, 0, slab(SlabType.BOTTOM));
        List<Grips.Face> faces = Grips.scan(w, CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.EAST));
        assertFalse(has(faces, 1, 0, 0, Direction.WEST));
        assertEquals(5, count(faces, 0, 0, 0));
        assertEquals(5, count(faces, 1, 0, 0));
    }

    /** A slab against a full block: the slab's side is covered, the full block's face is not (half of it is still open). */
    @Test
    void slabBesideStoneCoversItsOwnSideOnly() {
        World w = new World().put(0, 0, 0, slab(SlabType.BOTTOM)).put(1, 0, 0, stone());
        List<Grips.Face> faces = Grips.scan(w, CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.EAST));
        assertTrue(has(faces, 1, 0, 0, Direction.WEST));
    }

    /** Stairs (facing north, bottom half): the upper step's top is the UP face; the lower step is not on the outermost plane. */
    @Test
    void stairsStepTopIsAGrip() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, Blocks.STONE_STAIRS.defaultBlockState()), CENTRE, RADIUS);
        assertRect(new AABB(0, 1, 0, 1, 1, 0.5), rect(faces, 0, 0, 0, Direction.UP), "upper step top");
        assertRect(new AABB(0, 0, 1, 1, 0.5, 1), rect(faces, 0, 0, 0, Direction.SOUTH), "lower step front");
        assertRect(new AABB(0, 0, 0, 1, 1, 0), rect(faces, 0, 0, 0, Direction.NORTH), "back");
    }

    /** A thin block on the floor takes the floor Grip over: its own top is the Grip, at its real height. */
    @Test
    void thinCoversTakeTheFloorGrip() {
        Object[][] covers = {
                {slab(SlabType.BOTTOM), 0.5}, {Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3), 6 / 16.0}, {Blocks.WHITE_CARPET.defaultBlockState(), 1 / 16.0},
                {Blocks.FARMLAND.defaultBlockState(), 15 / 16.0}, {Blocks.DIRT_PATH.defaultBlockState(), 15 / 16.0},
                {Blocks.OAK_TRAPDOOR.defaultBlockState(), 3 / 16.0}, {Blocks.REPEATER.defaultBlockState(), 2 / 16.0}};
        for (Object[] c : covers) {
            BlockState cover = (BlockState) c[0];
            double top = 1 + (double) c[1];
            List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, cover), CENTRE, RADIUS);
            assertFalse(has(faces, 0, 0, 0, Direction.UP), "stone top under " + cover);
            assertRect(new AABB(0, top, 0, 1, top, 1), rect(faces, 0, 1, 0, Direction.UP), "top of " + cover);
            assertFalse(has(faces, 0, 1, 0, Direction.DOWN), "underside of " + cover);
        }
    }

    /** A top slab over the floor leaves a gap: the floor keeps its Grip and the slab's underside is one too. */
    @Test
    void topSlabOverTheFloorKeepsBothGrips() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, slab(SlabType.TOP)), CENTRE, RADIUS);
        assertTrue(has(faces, 0, 0, 0, Direction.UP));
        assertRect(new AABB(0, 1.5, 0, 1, 1.5, 1), rect(faces, 0, 1, 0, Direction.DOWN), "slab underside");
    }

    /** Create's Depot and Track on the floor: an upward Grip stays in the column, on the floor or on the block. */
    @Test
    void createCoversKeepAnUpwardGrip() {
        for (BlockState cover : new BlockState[]{AllBlocks.DEPOT.getDefaultState(), AllBlocks.TRACK.getDefaultState()}) {
            List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, cover), CENTRE, RADIUS);
            assertTrue(has(faces, 0, 0, 0, Direction.UP) || has(faces, 0, 1, 0, Direction.UP), cover + ": " + faces);
        }
    }

    @Test
    void chestLidIsAGripAtItsHeight() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, Blocks.CHEST.defaultBlockState()), CENTRE, RADIUS);
        assertRect(new AABB(1 / 16.0, 14 / 16.0, 1 / 16.0, 15 / 16.0, 14 / 16.0, 15 / 16.0), rect(faces, 0, 0, 0, Direction.UP), "lid");
        assertRect(new AABB(15 / 16.0, 0, 1 / 16.0, 15 / 16.0, 14 / 16.0, 15 / 16.0), rect(faces, 0, 0, 0, Direction.EAST), "side");
    }

    /** Nothing narrower than half a block is a Grip. */
    @Test
    void thinPostsAndChainsAreNoGrips() {
        for (BlockState thin : new BlockState[]{Blocks.OAK_FENCE.defaultBlockState(), Blocks.CHAIN.defaultBlockState(), Blocks.LIGHTNING_ROD.defaultBlockState()}) {
            List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, thin), CENTRE, RADIUS);
            assertEquals(0, count(faces, 0, 0, 0), thin + ": " + faces);
        }
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, Blocks.OAK_FENCE.defaultBlockState()), CENTRE, RADIUS);
        assertTrue(has(faces, 0, 0, 0, Direction.UP), "a fence post does not cover the floor");
    }

    /** A wall's post is half a block wide: its top is a Grip, at the block's top (walls collide 1.5 high, the Grip stays in the block). */
    @Test
    void wallTopIsAGrip() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, Blocks.COBBLESTONE_WALL.defaultBlockState()), CENTRE, RADIUS);
        assertRect(new AABB(4 / 16.0, 1, 4 / 16.0, 12 / 16.0, 1, 12 / 16.0), rect(faces, 0, 0, 0, Direction.UP), "post top");
        assertRect(new AABB(4 / 16.0, 0, 4 / 16.0, 4 / 16.0, 1, 12 / 16.0), rect(faces, 0, 0, 0, Direction.WEST), "post side");
        for (Grips.Face f : faces) assertTrue(new AABB(f.block()).inflate(EPS).contains(f.rect().getCenter()), "in its block: " + f);
    }

    /** A ladder on a wall: its back covers the wall, its rungs (3/16 out) are the Grip. A ladder on another wall leaves this one alone. */
    @Test
    void ladderOnTheWallIsTheGrip() {
        World w = new World().put(0, 0, 0, stone()).put(1, 0, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        List<Grips.Face> faces = Grips.scan(w, new Vec3(2.5, 0.5, 0.5), RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.EAST));
        assertRect(new AABB(1 + 3 / 16.0, 0, 0, 1 + 3 / 16.0, 1, 1), rect(faces, 1, 0, 0, Direction.EAST), "rungs");
        World other = new World().put(0, 0, 0, stone()).put(1, 0, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        assertTrue(has(Grips.scan(other, new Vec3(2.5, 0.5, 0.5), RADIUS), 0, 0, 0, Direction.EAST));
    }

    /** Nothing collides with one layer of snow or a hanging sign: no Grip, and the floor under the snow keeps its own. */
    @Test
    void noCollisionNoGrip() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, stone()).put(0, 1, 0, Blocks.SNOW.defaultBlockState()), CENTRE, RADIUS);
        assertEquals(0, count(faces, 0, 1, 0));
        assertTrue(has(faces, 0, 0, 0, Direction.UP));
        faces = Grips.scan(new World().put(0, 0, 0, Blocks.OAK_HANGING_SIGN.defaultBlockState()), CENTRE, RADIUS);
        assertEquals(0, count(faces, 0, 0, 0), faces.toString());
    }

    /** A face with a hole is only its largest solid part: a stair's side is its lower step; a hopper's top ring and a scaffolding's legs are nothing. */
    @Test
    void hollowFacesShrinkToTheirSolidPart() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 0, 0, Blocks.STONE_STAIRS.defaultBlockState()), CENTRE, RADIUS);
        assertRect(new AABB(1, 0, 0, 1, 0.5, 1), rect(faces, 0, 0, 0, Direction.EAST), "stair side");
        faces = Grips.scan(new World().put(0, 0, 0, Blocks.HOPPER.defaultBlockState()), CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.UP), "hopper top: " + faces);
        faces = Grips.scan(new World().put(0, 0, 0, Blocks.SCAFFOLDING.defaultBlockState()), CENTRE, RADIUS);
        assertRect(new AABB(0, 1, 0, 1, 1, 1), rect(faces, 0, 0, 0, Direction.UP), "scaffolding plank");
        assertEquals(1, count(faces, 0, 0, 0), "legs are no Grips: " + faces);
    }

    /** Scaffolding against a wall does not cover the wall (its side is hollow): the wall keeps its Grip. */
    @Test
    void scaffoldingLeavesTheWallGrip() {
        World w = new World().put(0, 0, 0, stone()).put(1, 0, 0, Blocks.SCAFFOLDING.defaultBlockState());
        assertTrue(has(Grips.scan(w, new Vec3(2.5, 0.5, 0.5), RADIUS), 0, 0, 0, Direction.EAST));
    }

    /** Stacked chests: the 2/16 seam between them holds no Grip; a wall covers the chest side against it across the 1/16 gap (the wall's own face stays: its rim is open). */
    @Test
    void thinSeamsAreCovered() {
        World w = new World().put(0, 0, 0, Blocks.CHEST.defaultBlockState()).put(0, 1, 0, Blocks.CHEST.defaultBlockState()).put(-1, 0, 0, stone());
        List<Grips.Face> faces = Grips.scan(w, CENTRE, RADIUS);
        assertFalse(has(faces, 0, 0, 0, Direction.UP), "lower lid");
        assertFalse(has(faces, 0, 1, 0, Direction.DOWN), "upper underside");
        assertTrue(has(faces, 0, 1, 0, Direction.UP), "upper lid");
        assertTrue(has(faces, -1, 0, 0, Direction.EAST), "wall behind the chest");
        assertFalse(has(faces, 0, 0, 0, Direction.WEST), "chest side at the wall");
        assertFalse(Grips.gripFace(w, BlockPos.ZERO, Direction.UP));
    }

    /** Leaves have an empty support shape but a full collision box: Grips as in 1.3.1, and in the way of a sight line. */
    @Test
    void leavesGripLikeFullBlocks() {
        World w = new World().put(0, 0, 0, Blocks.OAK_LEAVES.defaultBlockState());
        assertEquals(6, count(Grips.scan(w, CENTRE, RADIUS), 0, 0, 0));
        assertFalse(Grips.visible(w, new Vec3(0.5, -1.5, 0.5), new Vec3(0.5, 1, 0.5), Direction.UP, false), "a Grip on the leaves' top, seen from below");
        World canopy = new World().put(0, 0, 0, Blocks.OAK_LOG.defaultBlockState()).put(1, 0, 0, Blocks.OAK_LEAVES.defaultBlockState());
        assertFalse(has(Grips.scan(canopy, CENTRE, RADIUS), 0, 0, 0, Direction.EAST), "the log face inside the canopy is covered");
    }

    /** The server's rule ({@code faceRect} + {@code gripFace}) and the client's scan agree. */
    @Test
    void gripFaceRuleMatchesTheScan() {
        World w = new World().put(0, 0, 0, tank).put(1, 0, 0, tank).put(0, 4, 0, slab(SlabType.BOTTOM)).put(0, 6, 0, Blocks.OAK_FENCE.defaultBlockState())
                .put(0, 8, 0, stone()).put(0, 9, 0, Blocks.SNOW.defaultBlockState()).put(0, 11, 0, Blocks.CHEST.defaultBlockState()).put(0, 12, 0, Blocks.CHEST.defaultBlockState());
        List<Grips.Face> faces = Grips.scan(w, new Vec3(0.5, 6.5, 0.5), 8);
        for (int y = 0; y <= 12; y++)
            for (Direction d : Direction.values()) {
                BlockPos b = new BlockPos(0, y, 0);
                assertEquals(has(faces, 0, y, 0, d), Grips.gripFace(w, b, d), b + " " + d);
                AABB r = Grips.faceRect(w, b, d);
                if (has(faces, 0, y, 0, d)) assertRect(rect(faces, 0, y, 0, d), r, b + " " + d);
            }
        assertTrue(Grips.gripFace(w, new BlockPos(0, 4, 0), Direction.UP), "the slab top");
        assertTrue(Grips.gripFace(w, new BlockPos(0, 8, 0), Direction.UP), "the floor under one layer of snow");
        assertFalse(Grips.gripFace(w, BlockPos.ZERO, Direction.EAST), "the tank seam");
        assertNull(Grips.faceRect(w, new BlockPos(0, 6, 0), Direction.UP), "a fence post top");
        assertNull(Grips.faceRect(w, new BlockPos(0, 2, 0), Direction.UP), "air");
    }

    /** Going round a slab: a body above a slab's corner is Held by the slab's top and side alone (the outside-edge rule). */
    @Test
    void bodyOverASlabCornerIsHeld() {
        List<Grips.Face> faces = Grips.scan(new World().put(0, 4, 0, slab(SlabType.BOTTOM)), new Vec3(-0.5, 4.8, 0.5), RADIUS);
        assertTrue(Grips.held(faces, new Vec3(-0.5, 4.8, 0.5), 1.5));
        assertFalse(Grips.held(faces, new Vec3(-0.5, 6.5, 0.5), 1.5), "out of Reach");
    }
}
