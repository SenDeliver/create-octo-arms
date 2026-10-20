package dev.tentaworks.rig;

import dev.tentaworks.rig.GripFaceTest.World;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Grip must be in sight of the body (the cave report: a low body took the top of a cave floor and cave walls under the floor
 * it stood on, the Tentacles went through the floor). The sight line runs from the body centre to the Grip point lifted a
 * little off its face; any collision shape on the way makes the Grip invalid, on the client (gait) and on the server (packet check).
 */
class GripSightTest {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    /** A 7x7 floor at y=0 over a 7x7 cave floor at y=-3, walls at y -2..-1 round the cave. */
    private static World caveUnderFloor() {
        World w = new World();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                w.put(x, 0, z, STONE);
                w.put(x, -3, z, STONE);
                if (Math.abs(x) == 3 || Math.abs(z) == 3) w.put(x, -2, z, STONE).put(x, -1, z, STONE);
            }
        return w;
    }

    /** The body stands on the floor, its centre 0.9 up (feet at y=1). */
    private static final Vec3 BODY = new Vec3(0.5, 1.9, 0.5);

    @Test
    void floorTopIsInSight() {
        World w = caveUnderFloor();
        assertTrue(Grips.visible(w, BODY, new Vec3(2.5, 1, 2.5), Direction.UP, false), "the floor 2 blocks off");
        assertTrue(Grips.visible(w, BODY, new Vec3(0.5, 1, 0.5), Direction.UP, false), "the floor straight under the body");
        assertTrue(Grips.visible(w, BODY, new Vec3(-3, 1, 3), Direction.UP, false), "a floor corner, point on the block edge");
    }

    @Test
    void caveUnderTheFloorIsOutOfSight() {
        World w = caveUnderFloor();
        assertFalse(Grips.visible(w, BODY, new Vec3(1.5, -2, 1.5), Direction.UP, false), "the cave floor's top");
        assertFalse(Grips.visible(w, BODY, new Vec3(-2, -1.5, 0.5), Direction.EAST, false), "a cave wall facing the body's column");
        assertFalse(Grips.visible(w, BODY, new Vec3(0.5, -1.5, 3), Direction.NORTH, false), "the other cave wall");
    }

    @Test
    void farSideOfAWallIsOutOfSight() {
        World w = new World().put(2, 0, 0, STONE).put(2, 1, 0, STONE).put(2, 2, 0, STONE);
        Vec3 body = new Vec3(0.5, 0.9, 0.5);
        assertTrue(Grips.visible(w, body, new Vec3(2, 1.2, 0.5), Direction.WEST, false), "the near face");
        assertFalse(Grips.visible(w, body, new Vec3(3, 1.2, 0.5), Direction.EAST, false), "the far face, the body behind its plane");
        assertFalse(Grips.visible(w, new Vec3(0.5, 0.9, -0.5), new Vec3(2.5, 1.2, 1), Direction.SOUTH, false), "its south face from beside the wall: the line cuts the wall's corner");
    }

    /** A body just past a wall's end sees the face's edge but not its middle: the Tentacle would cut through the wall's corner. */
    @Test
    void wallSeenPastItsEndOnlyAtTheEdge() {
        World w = new World().put(0, 0, 0, STONE).put(0, 1, 0, STONE);
        Vec3 body = new Vec3(0.8, 0.9, -0.5);
        assertTrue(Grips.visible(w, body, new Vec3(1, 0.9, 0), Direction.EAST, false), "the edge of the east face");
        assertFalse(Grips.visible(w, body, new Vec3(1, 0.9, 0.8), Direction.EAST, false), "deep on the east face");
    }

    /** Recessed faces: a bottom slab's top (at half height), a top slab's underside, a chest lid. */
    @Test
    void recessedFacesAreInSight() {
        World w = new World().put(2, 0, 0, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM))
                .put(0, 3, 0, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)).put(-2, 0, 0, Blocks.CHEST.defaultBlockState());
        Vec3 body = new Vec3(0.5, 1.4, 0.5);
        assertTrue(Grips.visible(w, body, new Vec3(2.5, 0.5, 0.5), Direction.UP, false), "slab top");
        assertTrue(Grips.visible(w, body, new Vec3(0.5, 3.5, 0.5), Direction.DOWN, false), "top slab underside");
        assertTrue(Grips.visible(w, body, new Vec3(-1.5, 14 / 16.0, 0.5), Direction.UP, false), "chest lid");
        assertTrue(Grips.visible(w, body, new Vec3(2, 0.3, 0.5), Direction.WEST, false), "slab side, under its top");
    }

    /** An inner corner: a Grip on the floor right at a wall's foot, the point on both blocks' edge, is in sight. */
    @Test
    void floorPointAtAWallsFootIsInSight() {
        World w = new World();
        for (int x = -2; x <= 2; x++) w.put(x, 0, 0, STONE);
        w.put(2, 1, 0, STONE).put(2, 2, 0, STONE);
        assertTrue(Grips.visible(w, new Vec3(0.5, 1.9, 0.5), new Vec3(2, 1, 0.5), Direction.UP, false), "floor at the wall's foot");
        assertTrue(Grips.visible(w, new Vec3(0.5, 1.9, 0.5), new Vec3(2, 1, 0.5), Direction.WEST, false), "wall at the floor");
    }

    /** Thin blocks nothing collides with do not block the sight line; a fence post does only where the line meets its post. */
    @Test
    void noCollisionNoBlock() {
        World w = new World().put(0, 0, 0, STONE).put(0, 1, 0, Blocks.SNOW.defaultBlockState()).put(3, 0, 0, STONE);
        Vec3 body = new Vec3(0.5, 1.3, 0.5);
        assertTrue(Grips.visible(w, body, new Vec3(3, 0.5, 0.5), Direction.WEST, false), "over one layer of snow");
        w.put(1, 0, 0, STONE).put(2, 0, 0, STONE).put(1, 1, 0, Blocks.OAK_FENCE.defaultBlockState());
        assertFalse(Grips.visible(w, new Vec3(0.5, 1.5, 0.5), new Vec3(3, 1, 0.5), Direction.UP, false), "through a fence post");
        assertTrue(Grips.visible(w, new Vec3(0.5, 1.5, 0.5), new Vec3(2.5, 1, 0.9), Direction.UP, false), "past the post, between it and the block edge");
    }
}
