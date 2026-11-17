package dev.tentaworks.rig;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Actuators' guards against a Ship plot point taken for a world one (plots lie about 20 million blocks off). */
class ActuatorReachTest {
    private static final Vec3 EYE = new Vec3(11522.5, -58.4, 0.5);
    private static final Vec3 PLOT = new Vec3(20481032, 129.5, 20491273.5);

    /** A level any query fails on: a guarded call must not reach it. */
    private static final BlockGetter UNTOUCHED = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class},
            (proxy, method, args) -> {
                throw new AssertionError("level queried: " + method.getName());
            });

    @Test
    void segmentsUpToTwiceTheLargestReachAreSane() {
        assertTrue(ActuatorReach.sane(EYE, EYE.add(0, 0, ActuatorReach.MAX_SEGMENT)));
        assertFalse(ActuatorReach.sane(EYE, EYE.add(0, 0, ActuatorReach.MAX_SEGMENT + 0.01)));
        assertFalse(ActuatorReach.sane(EYE, PLOT));
        assertFalse(ActuatorReach.sane(EYE, EYE.lerp(PLOT, 0.3)));
        assertFalse(ActuatorReach.sane(EYE, new Vec3(Double.NaN, 0, 0)));
        assertFalse(ActuatorReach.sane(new Vec3(0, Double.POSITIVE_INFINITY, 0), EYE));
    }

    @Test
    void aTargetIsPlausibleWithinItsPickRangeAndSlack() {
        double range = ActuatorReach.pickRange(7);
        assertTrue(ActuatorReach.plausible(EYE, EYE.add(range + ActuatorReach.TARGET_SLACK, 0, 0), range));
        assertFalse(ActuatorReach.plausible(EYE, EYE.add(range + ActuatorReach.TARGET_SLACK + 0.01, 0, 0), range));
        assertFalse(ActuatorReach.plausible(EYE, PLOT, range));
        assertFalse(ActuatorReach.plausible(EYE, new Vec3(Double.NaN, 0, 0), range));
    }

    @Test
    void sightLinesTowardAPlotPointAreNotWalked() {
        AABB plotBox = new AABB(PLOT, PLOT.add(1, 1, 1));
        assertFalse(SetDown.sight(UNTOUCHED, EYE, plotBox, CollisionContext.empty()));
        assertFalse(WorkSight.seesBox(UNTOUCHED, plotBox, EYE, new Vec3(1, 0, 0), 8, CollisionContext.empty()));
        assertFalse(ActuatorReach.segment(EYE, PLOT, "test"));
    }
}
