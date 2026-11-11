package dev.tentaworks.rig;

import dev.tentaworks.rig.GripFaceTest.World;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Far Use past arm's length needs sight: the server's own pick finds the target, or a line from the eye to a visible point of it crosses no block. */
class WorkSightTest {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState GRASS = Blocks.SHORT_GRASS.defaultBlockState();
    private static final BlockState CARPET = Blocks.WHITE_CARPET.defaultBlockState();
    private static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    private static final BlockPos FLOOR5 = new BlockPos(5, 0, 0);
    private static final double RANGE = 8.0;

    private static World floor() {
        World w = new World();
        for (int x = -2; x <= 12; x++)
            for (int z = -3; z <= 3; z++) w.put(x, 0, z, STONE);
        return w;
    }

    private static Vec3 look(Vec3 at) {
        return at.subtract(EYE).normalize();
    }

    private static boolean sees(World w, BlockPos pos, Direction face, Vec3 point, Vec3 lookAt) {
        return WorkSight.seesBlock(w, pos, face, point, EYE, look(lookAt), RANGE, CollisionContext.empty());
    }

    @Test
    void theFloorAheadIsSeenWhereverTheLookLands() {
        World w = floor();
        Vec3 top = new Vec3(5.5, 1.0, 0.5);
        assertTrue(sees(w, FLOOR5, Direction.UP, top, top));
        assertTrue(sees(w, FLOOR5, Direction.UP, null, top));
        assertTrue(sees(w, FLOOR5, null, null, top));
        assertTrue(sees(w, FLOOR5, Direction.UP, new Vec3(5.02, 1.0, 0.98), new Vec3(4.6, 1.0, 0.5)));
        assertTrue(sees(w, FLOOR5, null, null, new Vec3(6.5, 1.0, 0.5)));
        assertTrue(sees(w, FLOOR5, Direction.UP, top, new Vec3(5.5, 1.0, 2.5)));
        assertTrue(sees(w, FLOOR5, null, null, new Vec3(30, 30, 0.5)));
    }

    @Test
    void everyFloorBlockOnALineAheadIsSeen() {
        World w = floor();
        for (int x = 2; x <= 7; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            Vec3 top = new Vec3(x + 0.5, 1.0, 0.5);
            for (double dx = -1.0; dx <= 1.0; dx += 0.25) {
                Vec3 lag = new Vec3(x + 0.5 + dx, 1.0, 0.5);
                assertTrue(sees(w, pos, Direction.UP, top, lag), "x=" + x + " dx=" + dx);
                assertTrue(sees(w, pos, null, null, lag), "x=" + x + " dx=" + dx);
            }
        }
    }

    @Test
    void aWallOrAPaneOfGlassHidesIt() {
        for (BlockState wall : new BlockState[] {STONE, GLASS}) {
            World w = floor();
            for (int y = 1; y <= 5; y++)
                for (int z = -3; z <= 3; z++) w.put(3, y, z, wall);
            Vec3 top = new Vec3(5.5, 1.0, 0.5);
            assertFalse(sees(w, FLOOR5, Direction.UP, top, top));
            assertFalse(sees(w, FLOOR5, null, null, top));
            BlockPos ore = new BlockPos(5, 2, 0);
            w.put(5, 2, 0, STONE);
            assertFalse(sees(w, ore, Direction.WEST, new Vec3(5.0, 2.5, 0.5), new Vec3(5.0, 2.5, 0.5)));
        }
    }

    @Test
    void aBlockStandingOnTheFloorInFrontHidesTheFloorBehindIt() {
        World w = floor().put(4, 1, 0, STONE);
        assertFalse(sees(w, FLOOR5, Direction.UP, new Vec3(5.02, 1.0, 0.5), new Vec3(4.5, 1.5, 0.5)));
        assertFalse(sees(w, FLOOR5, null, null, new Vec3(5.5, 1.0, 0.5)));
        assertTrue(sees(w, new BlockPos(5, 0, 2), Direction.UP, new Vec3(5.5, 1.0, 2.5), new Vec3(4.5, 1.5, 0.5)));
    }

    @Test
    void aTrenchFloorSeenOnlyNearItsFarEdgeIsSeenWhileTheLookLags() {
        World w = trench();
        BlockPos floor = new BlockPos(7, -1, 0);
        Vec3 behind = new Vec3(6.5, 0.0, 0.5);
        assertTrue(sees(w, floor, Direction.UP, null, new Vec3(7.9, 0.0, 0.5)));
        assertTrue(sees(w, floor, Direction.UP, null, behind));
        assertTrue(sees(w, floor, null, null, behind));
        assertTrue(sees(w, floor, Direction.UP, null, new Vec3(7.9, 0.0, 2.5)));
        assertFalse(sees(w, new BlockPos(6, -1, 0), Direction.UP, null, behind));
        assertFalse(sees(w, new BlockPos(5, -1, 0), Direction.UP, null, new Vec3(5.5, 0.0, 0.5)));
    }

    /** The floor one layer down with a trench at x 5..7 in the top layer: from the eye its floor shows only past x 7.78. */
    private static World trench() {
        World w = floor();
        for (int x = -2; x <= 12; x++)
            for (int z = -3; z <= 3; z++) w.put(x, -1, z, STONE);
        for (int x = 5; x <= 7; x++)
            for (int z = -3; z <= 3; z++) w.put(x, 0, z, AIR);
        return w;
    }

    @Test
    void plantsDoNotHideIt() {
        World w = floor().put(3, 1, 0, GRASS).put(4, 1, 0, GRASS);
        Vec3 top = new Vec3(5.5, 1.0, 0.5);
        assertTrue(sees(w, FLOOR5, Direction.UP, top, new Vec3(3.5, 1.2, 0.5)));
        assertTrue(sees(w, FLOOR5, null, null, top));
    }

    @Test
    void aThinBlockOnTheHitFaceIsTheClickedOneWhenTheClickLiesOnIt() {
        World w = floor().put(5, 1, 0, CARPET);
        BlockPos carpet = new BlockPos(5, 1, 0);
        BlockHitResult under = new BlockHitResult(new Vec3(5.5, 1.0, 0.5), Direction.UP, FLOOR5, false);
        assertTrue(WorkSight.picked(w, carpet, under, new Vec3(5.5, 1.06, 0.5)));
        assertFalse(WorkSight.picked(w, carpet, under, new Vec3(5.5, 1.5, 0.5)));
        assertFalse(WorkSight.picked(w, carpet, under, null));
        assertFalse(WorkSight.picked(w, new BlockPos(6, 1, 0), under, new Vec3(6.5, 1.0, 0.5)));
        assertTrue(WorkSight.picked(w, FLOOR5, under, null));
        assertFalse(WorkSight.picked(w, FLOOR5, BlockHitResult.miss(Vec3.ZERO, Direction.UP, FLOOR5), null));
    }

    @Test
    void aMobInTheOpenIsSeenOneBehindAWallIsNot() {
        World w = floor();
        AABB mob = new AABB(5.2, 1.0, 0.2, 5.8, 2.9, 0.8);
        Vec3 at = mob.getCenter();
        assertTrue(WorkSight.seesBox(w, mob, EYE, look(at), RANGE, CollisionContext.empty()));
        assertTrue(WorkSight.seesBox(w, mob, EYE, look(new Vec3(5.5, 4.0, 3.0)), RANGE, CollisionContext.empty()));
        assertTrue(WorkSight.seesBox(w, new AABB(0, 2, 0, 1, 3, 1), EYE, look(at), RANGE, CollisionContext.empty()));
        for (int y = 1; y <= 5; y++)
            for (int z = -3; z <= 3; z++) w.put(3, y, z, STONE);
        assertFalse(WorkSight.seesBox(w, mob, EYE, look(at), RANGE, CollisionContext.empty()));
    }
}
