package dev.tentaworks.client;

import dev.tentaworks.client.ActuatorSight.Mark;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorHud.CLOSE_TICKS;
import static dev.tentaworks.client.ActuatorHud.HINT_TICKS;
import static dev.tentaworks.client.ActuatorHud.closeIn;
import static dev.tentaworks.client.ActuatorHud.hint;
import static dev.tentaworks.client.ActuatorHud.hollow;
import static dev.tentaworks.client.ActuatorHud.lit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorHudTest {
    @Test
    void onlyTheActingSideShowsUnlessBothArmsWork() {
        assertTrue(lit(Mark.REACH, true));
        assertFalse(lit(Mark.REACH, false), "a plain reach: the acting side only");
        for (Mark both : new Mark[]{Mark.DUAL, Mark.OUT, Mark.LIMP}) {
            assertTrue(lit(both, true), both.name());
            assertTrue(lit(both, false), both.name());
        }
        assertFalse(lit(Mark.NONE, true));
        assertFalse(lit(Mark.NONE, false));
    }

    @Test
    void onlyOutOfReachIsHollow() {
        for (Mark m : Mark.values()) assertEquals(m == Mark.OUT, hollow(m), m.name());
    }

    @Test
    void theMarksCloseInWithinATickAndLetGo() {
        assertEquals(0, closeIn(0));
        assertEquals(1, closeIn(1), 1.0E-6, "closed one tick after the action fired");
        assertEquals(0, closeIn(CLOSE_TICKS));
        assertEquals(0, closeIn(-3), "nothing fired yet");
        float last = 1;
        for (float age = 1; age <= CLOSE_TICKS; age += 0.25F) {
            float k = closeIn(age);
            assertTrue(k >= 0 && k <= last + 1.0E-6F, "opens again without a jump at " + age);
            last = k;
        }
    }

    @Test
    void theHintRowShowsForTheFirstSecondsOfAHoldAndFades() {
        assertEquals(1, hint(0));
        assertEquals(1, hint(HINT_TICKS / 2F));
        assertTrue(hint(HINT_TICKS - 3) > 0 && hint(HINT_TICKS - 3) < 1, "fading");
        assertEquals(0, hint(HINT_TICKS));
        assertEquals(0, hint(-1));
    }
}
