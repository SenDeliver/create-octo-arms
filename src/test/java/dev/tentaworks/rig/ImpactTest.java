package dev.tentaworks.rig;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Impact damage = clamp((speed − impact.minSpeed) × impact.damagePerSpeed, 0, impact.cap), dealt once per Throw. */
class ImpactTest {
    private static final double MIN = 0.3, PER = 12, CAP = 20;

    @Test
    void belowAndAtTheMinimum() {
        assertEquals(0, Impact.damage(0, MIN, PER, CAP));
        assertEquals(0, Impact.damage(0.29, MIN, PER, CAP));
        assertEquals(0, Impact.damage(MIN, MIN, PER, CAP));
    }

    @Test
    void growsWithSpeed() {
        assertEquals(6.0, Impact.damage(0.8, MIN, PER, CAP), 1.0E-5, "a Copper Throw into a wall");
        assertEquals(10.8, Impact.damage(1.2, MIN, PER, CAP), 1.0E-5, "a Netherite Throw into a wall");
    }

    @Test
    void capped() {
        double atCap = MIN + CAP / PER;
        assertEquals(CAP, Impact.damage(atCap, MIN, PER, CAP), 1.0E-5);
        assertEquals(CAP, Impact.damage(5, MIN, PER, CAP));
        assertEquals(0, Impact.damage(5, MIN, PER, 0));
    }

    @Test
    void serverConfigDefaults() {
        assertEquals(6.0, Impact.damage(0.8), 1.0E-5);
        assertEquals(20, Impact.damage(3));
    }

    @Test
    void speedIntoTheSurface() {
        Vec3 skid = new Vec3(0.8, -0.08, 0);
        assertEquals(0.08, Impact.intoSurface(skid, false, true), 1.0E-9, "landing at a flat angle: the vertical part");
        assertEquals(0, Impact.damage(Impact.intoSurface(skid, false, true), MIN, PER, CAP));
        assertEquals(0.8, Impact.intoSurface(skid, true, false), 1.0E-9, "into a wall: the horizontal part");
        assertEquals(skid.length(), Impact.intoSurface(skid, true, true), 1.0E-9, "into a corner");
        assertEquals(0, Impact.intoSurface(skid, false, false));
    }

    @Test
    void onceThenDisarmed() {
        Thrown t = new Thrown(UUID.randomUUID(), Vec3.ZERO, 0.8, true);
        assertTrue(t.armed());
        Thrown spent = t.disarmed();
        assertFalse(spent.armed());
        assertFalse(spent.withSpeed(1.0).armed(), "speed updates keep it spent");
        assertEquals(t.start(), spent.start());
    }
}
