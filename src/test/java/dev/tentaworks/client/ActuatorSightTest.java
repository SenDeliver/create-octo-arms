package dev.tentaworks.client;

import dev.tentaworks.client.ActuatorSight.Mark;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorSight.OUT_PAST;
import static dev.tentaworks.client.ActuatorSight.mark;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void justOutOfReachShowsUpToThreeBlocksPast() {
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
}
