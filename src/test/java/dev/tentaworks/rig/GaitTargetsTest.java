package dev.tentaworks.rig;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gait's spread targets: back slots sink with Reach but never under the wearer's floor (the cave report). */
class GaitTargetsTest {
    private static final double EPS = 1.0E-9;
    private static final Vec3 BODY = new Vec3(0.5, 10.9, 0.5);

    @Test
    void backSlotsSinkByReachWithNoFloor() {
        Vec3[] t = TentacleGait.targets(BODY, 0, 7, Double.NaN);
        assertEquals(BODY.y + 0.35 * 7, t[0].y, EPS, "front left");
        assertEquals(BODY.y + 0.35 * 7, t[1].y, EPS, "front right");
        assertEquals(BODY.y - 0.35 * 7, t[2].y, EPS, "back left");
        assertEquals(BODY.y - 0.35 * 7, t[3].y, EPS, "back right");
    }

    /** Feet on the floor: Netherite's back aim would be 1.55 under it, Copper's 0.5; both land on the floor. */
    @Test
    void backSlotsStopAtTheFloor() {
        double floor = BODY.y - 0.9;
        for (double reach : new double[]{4, 7}) {
            Vec3[] free = TentacleGait.targets(BODY, 0, reach, Double.NaN);
            Vec3[] t = TentacleGait.targets(BODY, 0, reach, floor);
            for (int i = 2; i < 4; i++) {
                assertEquals(floor, t[i].y, EPS, "back slot " + i + " at Reach " + reach);
                assertEquals(free[i].x, t[i].x, EPS);
                assertEquals(free[i].z, t[i].z, EPS);
            }
            for (int i = 0; i < 2; i++) assertEquals(free[i], t[i], "front slot " + i + " untouched");
        }
    }

    /** A floor deeper than the aim changes nothing: the Standing Height case. */
    @Test
    void aFloorBelowTheAimIsNoClamp() {
        Vec3[] free = TentacleGait.targets(BODY, 90, 4, Double.NaN);
        Vec3[] t = TentacleGait.targets(BODY, 90, 4, BODY.y - 3);
        for (int i = 0; i < 4; i++) assertEquals(free[i], t[i]);
        assertTrue(t[2].y > BODY.y - 3);
    }
}
