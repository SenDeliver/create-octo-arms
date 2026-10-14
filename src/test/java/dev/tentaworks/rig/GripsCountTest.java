package dev.tentaworks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Held count on world faces: quadrants, and the outside-edge rule for a pole seen from its corner. */
class GripsCountTest {
    private static final double REACH = 4;

    /** Exposed faces of a free-standing 1x1 pole at x 0..1, z 0..1 (the four sides), y 0..height. */
    private static List<Grips.Face> pole(int height) {
        List<Grips.Face> faces = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            BlockPos b = new BlockPos(0, y, 0);
            faces.add(new Grips.Face(b, Direction.WEST, new AABB(0, y, 0, 0, y + 1, 1), null));
            faces.add(new Grips.Face(b, Direction.EAST, new AABB(1, y, 0, 1, y + 1, 1), null));
            faces.add(new Grips.Face(b, Direction.NORTH, new AABB(0, y, 0, 1, y + 1, 0), null));
            faces.add(new Grips.Face(b, Direction.SOUTH, new AABB(0, y, 1, 1, y + 1, 1), null));
        }
        return faces;
    }

    @Test
    void faceHoldsByTwoQuadrants() {
        assertEquals(2, Grips.count(pole(10), new Vec3(1.8, 5, 0.5), REACH));
    }

    @Test
    void cornerHoldsByTheEdge() {
        List<Grips.Face> faces = pole(10);
        for (double[] off : new double[][]{{0.35, 0.35}, {0.7, 0.4}, {1.2, 0.9}, {2.5, 2.5}})
            assertTrue(Grips.held(faces, new Vec3(1 + off[0], 5, 1 + off[1]), REACH), "offset " + off[0] + "/" + off[1]);
    }

    @Test
    void edgeOutOfReachDoesNotHold() {
        assertFalse(Grips.held(pole(10), new Vec3(1 + 3.0, 5, 1 + 3.0), REACH));
    }

    @Test
    void oneFaceOffToTheSideIsOneQuadrant() {
        List<Grips.Face> wall = new ArrayList<>();
        for (int z = -5; z < 1; z++) wall.add(new Grips.Face(new BlockPos(0, 5, z), Direction.EAST, new AABB(1, 5, z, 1, 6, z + 1), null));
        assertEquals(1, Grips.count(wall, new Vec3(1.8, 5.5, 1.6), REACH));
    }
}
