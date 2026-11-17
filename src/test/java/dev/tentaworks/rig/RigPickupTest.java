package dev.tentaworks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pickup while carried: the strip under vanilla's pickup box reaches the floor under a wearer at Standing Height, never a floor below it. */
class RigPickupTest {
    private static final double FLOOR = -60;

    private static AABB vanilla(double feet) {
        return new AABB(-0.3, feet, -0.3, 0.3, feet + 1.8, 0.3).inflate(1, RigPickup.SLACK, 1);
    }

    private static AABB item(double x, double y) {
        return new AABB(x - 0.125, y, -0.125, x + 0.125, y + 0.25, 0.125);
    }

    @Test
    void theFloorUnderAWearerAtStandingHeightIsReached() {
        double feet = FLOOR + 1.5;
        double below = RigPickup.below(feet, FLOOR, 1.5);
        assertEquals(1.5, below, 1e-9);
        AABB strip = RigPickup.strip(vanilla(feet), feet, below);
        assertFalse(vanilla(feet).intersects(item(0, FLOOR)), "vanilla misses the floor 1.5 under the feet");
        assertTrue(strip.intersects(item(0, FLOOR)));
        assertTrue(strip.intersects(item(1.2, FLOOR)), "as wide as vanilla's box");
        assertEquals(vanilla(feet).minY, strip.maxY, 1e-9);
    }

    @Test
    void aFloorFoundNearerCutsTheStripAboveTheRoomBelow() {
        double feet = FLOOR + 0.8;
        AABB strip = RigPickup.strip(vanilla(feet), feet, RigPickup.below(feet, FLOOR, 3));
        assertTrue(strip.intersects(item(0, FLOOR)));
        assertFalse(strip.intersects(item(0, FLOOR - 1.25)), "an item under a one-block floor");
    }

    @Test
    void noFloorWithinStandingHeightReachesStandingHeightOnly() {
        double feet = FLOOR + 4;
        double below = RigPickup.below(feet, Double.NaN, 1.5);
        assertEquals(1.5, below, 1e-9);
        assertFalse(RigPickup.strip(vanilla(feet), feet, below).intersects(item(0, FLOOR)));
    }

    @Test
    void onTheFloorVanillaAlreadyReachesIt() {
        assertNull(RigPickup.strip(vanilla(FLOOR), FLOOR, RigPickup.below(FLOOR, FLOOR, 1.5)));
        assertNull(RigPickup.strip(vanilla(FLOOR), FLOOR, RigPickup.below(FLOOR, Double.NaN, 0)));
    }

    @Test
    void aFloorBlockUnderAWearerAtStandingHeightLiesOutsideVanillasPickupBox() {
        double feet = FLOOR + 1.5;
        assertFalse(new AABB(new BlockPos(0, (int) FLOOR - 1, 0)).intersects(vanilla(feet)));
        assertTrue(new AABB(new BlockPos(0, (int) FLOOR - 1, 0)).intersects(vanilla(FLOOR)));
    }
}
