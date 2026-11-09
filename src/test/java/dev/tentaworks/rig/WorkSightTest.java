package dev.tentaworks.rig;

import dev.tentaworks.rig.GripFaceTest.World;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Work past the player's own range needs sight (Far Use through walls): the line from the eye or the body crosses no block. */
class WorkSightTest {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState GRASS = Blocks.SHORT_GRASS.defaultBlockState();
    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 BODY = new Vec3(0.5, 0.9, 0.5);
    private static final BlockPos ORE = new BlockPos(8, 1, 0);

    @Test
    void sightIsNeededOnlyPastTheOwnRangePlusVanillaSlack() {
        assertFalse(WorkSight.needsSight(5.5 * 5.5, 4.5));
        assertFalse(WorkSight.needsSight(0, 4.5));
        assertTrue(WorkSight.needsSight(5.51 * 5.51, 4.5));
        assertTrue(WorkSight.needsSight(10 * 10, 4.5));
        assertTrue(WorkSight.needsSight(4.01 * 4.01, 3.0));
    }

    @Test
    void anOpenLineSeesTheBlock() {
        World w = new World().put(8, 1, 0, STONE);
        assertTrue(WorkSight.seesBlock(w, ORE, null, EYE, BODY));
        assertTrue(WorkSight.seesBlock(w, ORE, new Vec3(8.0, 1.5, 0.5), EYE, BODY));
    }

    @Test
    void aWallOrAPaneOfGlassHidesIt() {
        World w = new World().put(8, 1, 0, STONE);
        for (int y = -1; y <= 4; y++)
            for (int z = -3; z <= 3; z++) w.put(4, y, z, STONE);
        assertFalse(WorkSight.seesBlock(w, ORE, new Vec3(8.0, 1.5, 0.5), EYE, BODY));
        World g = new World().put(8, 1, 0, STONE);
        for (int y = -1; y <= 4; y++)
            for (int z = -3; z <= 3; z++) g.put(4, y, z, GLASS);
        assertFalse(WorkSight.seesBlock(g, ORE, null, EYE, BODY));
    }

    @Test
    void plantsDoNotHideItAndTheBlockItselfDoesNot() {
        World w = new World().put(8, 1, 0, STONE).put(4, 1, 0, GRASS).put(4, 0, 0, GRASS);
        assertTrue(WorkSight.seesBlock(w, ORE, null, EYE, BODY));
        World top = new World().put(8, 1, 0, STONE);
        assertTrue(WorkSight.seesBlock(top, ORE, new Vec3(8.9, 2.0, 0.9), EYE, BODY));
    }

    @Test
    void theEyeSeesOverAHalfWallTheBodyDoesNot() {
        World w = new World().put(8, 1, 0, STONE);
        for (int z = -3; z <= 3; z++) w.put(4, 0, z, STONE).put(4, 1, z, STONE);
        assertFalse(WorkSight.seesBlock(w, ORE, null, BODY));
        assertTrue(WorkSight.seesBlock(w, new BlockPos(8, 2, 0), null, EYE, BODY));
    }
}
