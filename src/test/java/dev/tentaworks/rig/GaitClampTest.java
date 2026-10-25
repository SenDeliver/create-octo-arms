package dev.tentaworks.rig;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Grip points keep inside a face's edges, so a pad sits on the face instead of hanging half off a ledge or digging into a corner. */
class GaitClampTest {
    private static final double EPS = 1.0E-9;
    /** The top face of a full block at y = 10..11: a zero-thickness rectangle at y = 11. */
    private static final AABB TOP = new AABB(0, 11, 0, 1, 11, 1);

    @Test
    void anAimPastTheEdgeStopsInsideIt() {
        Vec3 p = TentacleGait.clampTo(new Vec3(3, 12, -2), TOP);
        assertEquals(1 - TentacleGait.EDGE, p.x, EPS);
        assertEquals(11, p.y, EPS, "the face plane itself is kept");
        assertEquals(TentacleGait.EDGE, p.z, EPS);
    }

    @Test
    void anAimOnTheFaceIsUntouched() {
        Vec3 p = TentacleGait.clampTo(new Vec3(0.5, 10.2, 0.7), TOP);
        assertEquals(new Vec3(0.5, 11, 0.7), p);
    }

    /** A half-block face (the top of a bottom slab seen from the side is 0.5 wide): the inset never crosses the centre. */
    @Test
    void aSmallFaceInsetsToItsCentreAtMost() {
        AABB narrow = new AABB(0, 11, 0, 0.2, 11, 1);
        Vec3 p = TentacleGait.clampTo(new Vec3(5, 11, 0.5), narrow);
        assertEquals(0.1, p.x, EPS);
    }
}
