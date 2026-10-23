package dev.tentaworks.client;

import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.1.1 render guard: a Grip is drawn only when it is a finite point within MAX_GRIP_DISTANCE of its wearer. On 1.21.1
 * the obs* suites covered it through Ships; this branch has no Ships, so this test is its only automatic check.
 */
class RigVisualsPlausibleTest {
    private static final Vec3 WEARER = new Vec3(100.5, 70.0, -20.5);

    @Test
    void maxDistanceIsTwiceTheReachCap() {
        assertEquals(TentacleRigItem.MAX_REACH * 2, RigVisuals.MAX_GRIP_DISTANCE);
        assertEquals(32.0, RigVisuals.MAX_GRIP_DISTANCE);
    }

    @Test
    void withinTheDistance() {
        assertTrue(RigVisuals.plausible(WEARER, WEARER));
        assertTrue(RigVisuals.plausible(WEARER, WEARER.add(16, -3, 4)));
        assertTrue(RigVisuals.plausible(WEARER, WEARER.add(0, RigVisuals.MAX_GRIP_DISTANCE, 0)));
        assertTrue(RigVisuals.plausible(WEARER, WEARER.add(-RigVisuals.MAX_GRIP_DISTANCE, 0, 0)));
    }

    @Test
    void beyondTheDistance() {
        assertFalse(RigVisuals.plausible(WEARER, WEARER.add(0, 0, RigVisuals.MAX_GRIP_DISTANCE + 1.0E-6)));
        assertFalse(RigVisuals.plausible(WEARER, WEARER.add(23, 23, 0)));
        assertFalse(RigVisuals.plausible(WEARER, new Vec3(30_000_000, 70, 30_000_000)));
    }

    @Test
    void nonFiniteNeverDrawn() {
        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double b : bad) {
            assertFalse(RigVisuals.plausible(WEARER, new Vec3(b, WEARER.y, WEARER.z)));
            assertFalse(RigVisuals.plausible(WEARER, new Vec3(WEARER.x, b, WEARER.z)));
            assertFalse(RigVisuals.plausible(WEARER, new Vec3(WEARER.x, WEARER.y, b)));
        }
    }
}
