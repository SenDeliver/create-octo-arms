package dev.tentaworks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn Step phase follows the wire exactly for the wearer, and never freezes, jumps or teleports for anyone else. */
class StepPhaseTest {
    private static final float EPS = 1.0E-5F;

    /** The wearer's own 2-tick and 7-tick Steps: drawn phase equals the gait's phase every tick (what shipped before). */
    @Test
    void ownGaitIsReproducedExactly() {
        for (int ticks : new int[]{2, 3, 7}) {
            StepPhase p = new StepPhase();
            p.start(0);
            assertEquals(0, p.phase, EPS);
            for (int t = 1; t <= ticks; t++) {
                float wire = Math.min(1, (float) t / ticks);
                boolean done = p.advance(wire);
                assertEquals(wire, p.phase, EPS, ticks + "-tick Step, tick " + t);
                assertEquals(t == ticks, done, "planted on the last tick only");
            }
        }
    }

    /** A Step first seen already planted (Activation, mid-air catch, remote under loss) arcs over 4 ticks instead of snapping. */
    @Test
    void aStepSeenDoneIsDrivenOverFourTicks() {
        StepPhase p = new StepPhase();
        p.start(1);
        assertEquals(0, p.phase, EPS);
        assertFalse(p.advance(1));
        assertEquals(0.25F, p.phase, EPS);
        assertFalse(p.advance(1));
        assertFalse(p.advance(1));
        assertTrue(p.advance(1));
        assertEquals(1, p.phase, EPS);
        assertFalse(p.advance(1), "planted once");
    }

    /** A tick without a packet (wire phase unchanged) still moves the arc; the next packet catches up without going backwards. */
    @Test
    void aMissedPacketDoesNotFreezeTheArc() {
        StepPhase p = new StepPhase();
        p.start(0);
        p.advance(0.5F);
        p.advance(0.5F);
        assertEquals(0.5F + StepPhase.MIN_RATE, p.phase, EPS, "kept moving");
        float before = p.phase;
        p.advance(0.75F);
        assertTrue(p.phase >= before, "never backwards");
        assertTrue(p.advance(1));
    }

    /** The target changes mid-arc (the gait keeps its phase): the arc restarts from 0 at the gait's pace and still completes. */
    @Test
    void aRetargetMidArcRestartsAndCompletes() {
        StepPhase p = new StepPhase();
        p.start(0);
        p.advance(0.6F);
        p.start(0.6F);
        assertEquals(0, p.phase, EPS);
        assertFalse(p.advance(0.8F));
        assertEquals(0.2F, p.phase, EPS);
        int ticks = 0;
        while (!p.advance(1) && ticks < 20) ticks++;
        assertTrue(ticks < 6, "completes within a few ticks, took " + ticks);
    }

    @Test
    void interpolatesBetweenTicks() {
        StepPhase p = new StepPhase();
        p.start(0);
        p.advance(0.5F);
        assertEquals(0.25F, p.at(0.5F), EPS);
        p.settle();
        assertEquals(1, p.phase, EPS);
        assertFalse(p.stepping());
    }
}
