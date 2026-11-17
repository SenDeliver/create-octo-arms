package dev.tentaworks.rig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The heavy-Ship curve with the default knee 20 and the Netherite cap 6000: servo, wearer speed and Turn as designed. */
class ShipWeightTest {
    private static final double CAP = 6000, KNEE = 20;
    private static final double[] MASS = {10, 100, 1000, 6000};

    private static double x(double m) {
        return ShipWeight.x(m, CAP, KNEE);
    }

    @Test
    void servoFactor() {
        double[] want = {0.936, 0.717, 0.380, 0.100};
        for (int i = 0; i < MASS.length; i++) assertEquals(want[i], ShipWeight.factor(x(MASS[i]), 0.1), 5.0E-4, "mass " + MASS[i]);
    }

    @Test
    void wearerSpeedFactor() {
        double[] want = {0.568, 0.459, 0.290, 0.150};
        for (int i = 0; i < MASS.length; i++) assertEquals(want[i], 0.6 * ShipWeight.factor(x(MASS[i]), 0.25), 5.0E-4, "mass " + MASS[i]);
    }

    @Test
    void turnDegreesPerTick() {
        double[] want = {8.46, 6.60, 3.73, 1.35};
        for (int i = 0; i < MASS.length; i++) assertEquals(want[i], Hold.TURN_RATE * ShipWeight.factor(x(MASS[i]), 0.15), 5.0E-3, "mass " + MASS[i]);
    }

    @Test
    void theCurveIsClampedAndQuantised() {
        assertEquals(0, x(0));
        assertEquals(0, x(-1), "unknown mass is light");
        assertEquals(1, x(9000), "over the cap stays at the floor");
        assertEquals(1, ShipWeight.x(5, 0, KNEE), "a zero cap: any mass is at the cap");
        ShipWeight.Factors f = ShipWeight.of(100, CAP, new ShipWeight.Curve(KNEE, 0.1, 0.25, 0.15, 0.1));
        assertEquals(0.72, f.servo(), 1.0E-9);
        assertEquals(0.76, f.walk(), 1.0E-9);
        assertEquals(0.73, f.turn(), 1.0E-9);
        assertEquals(0.72, f.hurl(), 1.0E-9);
        assertEquals(ShipWeight.Factors.NONE, ShipWeight.of(0, CAP, new ShipWeight.Curve(KNEE, 0.1, 0.25, 0.15, 0.1)));
        assertEquals(0.01, ShipWeight.quantise(0.0001), 1.0E-12);
    }

    @Test
    void overTheCapWithHysteresis() {
        assertFalse(ShipWeight.over(6000, CAP));
        assertFalse(ShipWeight.over(6299, CAP), "within 5 %");
        assertTrue(ShipWeight.over(6301, CAP));
    }

    @Test
    void aSlowHoldGetsMoreFarTicks() {
        assertEquals(5, ShipWeight.farTicks(5, 1));
        assertEquals(7, ShipWeight.farTicks(5, 0.72));
        assertEquals(50, ShipWeight.farTicks(5, 0.1));
    }
}
