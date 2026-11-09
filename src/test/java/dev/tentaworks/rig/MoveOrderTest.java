package dev.tentaworks.rig;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A move whose end depends on which horizontal axis vanilla collides first is cut to the part both orders reach, so the
 * server's replay of the client's delta lands where the client did (2.0 pole suite: 134 "moved wrongly" going round a 1x1 log
 * at the default 8 b/s, a 45 deg step of 0.283 per axis past vanilla's 0.25 tolerance).
 */
class MoveOrderTest {
    private static final List<VoxelShape> POLE = List.of(Shapes.create(new AABB(5000, -60, 0, 5001, -19, 1)));
    private static final Grips.AxisSweep SWEEP = (axis, box, d) -> Shapes.collide(axis, box, POLE, d);
    private static final double STEP = 0.4 / Math.sqrt(2);

    private static AABB player(double x, double y, double z) {
        return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    @Test
    void diagonalPastTheCornerKeepsTheFreeAxis() {
        Vec3 m = new Vec3(-STEP, 0, STEP);
        Vec3 safe = Grips.orderSafe(player(5001.3, -46.903, 1.066), m, SWEEP);
        assertEquals(0, safe.x, 1.0E-9);
        assertEquals(STEP, safe.z, 1.0E-9);
    }

    @Test
    void sameResultInBothOrdersIsUntouched() {
        Vec3 m = new Vec3(-STEP, 0, STEP);
        assertSame(m, Grips.orderSafe(player(5001.3, -46.903, 1.349), m, SWEEP));
        assertSame(m, Grips.orderSafe(player(5010, -46.903, 10), m, SWEEP));
    }

    @Test
    void pressedFlatOnAFaceIsUntouched() {
        Vec3 m = new Vec3(-STEP, 0.1, STEP);
        assertSame(m, Grips.orderSafe(player(5001.3, -46.903, 0.5), m, SWEEP));
    }

    @Test
    void singleAxisMoveIsUntouched() {
        Vec3 m = new Vec3(0, 0.4, STEP);
        assertSame(m, Grips.orderSafe(player(5001.3, -46.903, 1.066), m, SWEEP));
    }
}
