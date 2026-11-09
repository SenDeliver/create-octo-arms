package dev.tentaworks.client;

import dev.tentaworks.client.ActuatorClient.Affordance;
import dev.tentaworks.client.ActuatorClient.Affordance.Kind;
import dev.tentaworks.client.ActuatorSight.Mark;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.HoldArm;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorSight.MISS_PAST;
import static dev.tentaworks.client.ActuatorSight.OUT_PAST;
import static dev.tentaworks.client.ActuatorSight.mark;
import static dev.tentaworks.client.ActuatorSight.misses;
import static dev.tentaworks.client.ActuatorSight.shown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorSightTest {
    @Test
    void marksShowOnlyWhatAnActuatorIsNeededFor() {
        assertEquals(Mark.NONE, mark(false, false, true, true, true, true, -1, false), "no Actuators, no marks");
        assertEquals(Mark.NONE, mark(true, false, true, false, true, true, -1, false), "nothing under the crosshair");
        assertEquals(Mark.NONE, mark(true, false, true, true, false, true, -3, false), "within arm's length the crosshair stays plain");
        assertEquals(Mark.REACH, mark(true, false, true, true, true, true, -1, false), "past arm's length, in Reach");
        assertEquals(Mark.NONE, mark(true, false, false, true, true, true, -1, false), "Far Use off: no promise");
    }

    @Test
    void justOutOfReachShowsOnlyABlockAndAHalfPast() {
        assertEquals(1.5, OUT_PAST);
        assertEquals(Mark.OUT, mark(true, false, true, true, true, false, 0.1, false));
        assertEquals(Mark.OUT, mark(true, false, true, true, true, false, OUT_PAST, false));
        assertEquals(Mark.NONE, mark(true, false, true, true, true, false, OUT_PAST + 0.1, false));
        assertEquals(Mark.OUT, mark(true, true, true, true, true, false, 1, false), "out of Reach stays grey without Air too");
    }

    @Test
    void limpIsRedAndDualWieldLightsBoth() {
        assertEquals(Mark.LIMP, mark(true, true, true, true, true, true, -1, false));
        assertEquals(Mark.NONE, mark(true, true, true, true, false, true, -3, false), "limp, but a hand reaches it");
        assertEquals(Mark.DUAL, mark(true, false, true, true, true, true, -1, true));
        assertEquals(Mark.DUAL, mark(true, false, true, true, false, true, -3, true), "Dual Wield shows at arm's length too");
        assertEquals(Mark.LIMP, mark(true, true, true, true, true, true, -1, true), "no Air, no Dual Wield");
    }

    @Test
    void aClickOnNothingFlashesOnlyWhenATargetIsALittlePastReach() {
        assertTrue(misses(true, false, true, 0.5));
        assertTrue(misses(true, false, true, MISS_PAST), "farther than the resting marks show");
        assertFalse(misses(true, false, true, MISS_PAST + 0.1), "the horizon: a plain miss");
        assertFalse(misses(true, false, false, 0.5), "the sky");
        assertFalse(misses(true, true, true, 0.5), "the crosshair has it: the click lands");
        assertFalse(misses(false, false, true, 0.5), "Far Use off or a heavy hold: no promise, no flash");
        assertFalse(misses(true, false, true, 0), "in Reach");
    }

    @Test
    void neverGrabbedThingsGetNoCue() {
        assertEquals(Kind.NONE, shown(new Affordance(Kind.REFUSED, HoldArm.NONE, GrabRule.Verdict.PLAYER)).kind());
        assertEquals(Kind.NONE, shown(new Affordance(Kind.REFUSED, HoldArm.NONE, GrabRule.Verdict.NOT_LIVING)).kind());
        assertEquals(Kind.REFUSED, shown(new Affordance(Kind.REFUSED, HoldArm.NONE, GrabRule.Verdict.BOSS)).kind(), "a boss says why");
        assertEquals(Kind.GRAB, shown(new Affordance(Kind.GRAB, HoldArm.OFF, GrabRule.Verdict.LIGHT)).kind());
        assertEquals(Kind.HANDS_FULL, shown(new Affordance(Kind.HANDS_FULL, HoldArm.NONE, null)).kind());
    }
}
