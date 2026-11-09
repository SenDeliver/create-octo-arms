package dev.tentaworks.client;

import dev.tentaworks.client.ActuatorSight.Mark;
import dev.tentaworks.rig.GrabRule;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorHud.CLOSE_PEAK;
import static dev.tentaworks.client.ActuatorHud.CLOSE_TICKS;
import static dev.tentaworks.client.ActuatorHud.CUE_TEXT_FADE;
import static dev.tentaworks.client.ActuatorHud.CUE_TEXT_TICKS;
import static dev.tentaworks.client.ActuatorHud.HINT_TICKS;
import static dev.tentaworks.client.ActuatorHud.MISS_TICKS;
import static dev.tentaworks.client.ActuatorHud.closeIn;
import static dev.tentaworks.client.ActuatorHud.cueText;
import static dev.tentaworks.client.ActuatorHud.hint;
import static dev.tentaworks.client.ActuatorHud.hollow;
import static dev.tentaworks.client.ActuatorHud.lit;
import static dev.tentaworks.client.ActuatorHud.miss;
import static dev.tentaworks.client.ActuatorHud.missShake;
import static dev.tentaworks.client.ActuatorHud.throwKeys;
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
    void theMarksCloseInWithAStrikeAndLetGo() {
        assertEquals(0, closeIn(0));
        assertEquals(1, closeIn(CLOSE_PEAK), 1.0E-6, "closed at the strike");
        assertEquals(0, closeIn(CLOSE_TICKS));
        assertEquals(0, closeIn(-3), "nothing fired yet");
        float last = 1;
        for (float age = CLOSE_PEAK; age <= CLOSE_TICKS; age += 0.25F) {
            float k = closeIn(age);
            assertTrue(k >= 0 && k <= last + 1.0E-6F, "opens again without a jump at " + age);
            last = k;
        }
    }

    @Test
    void continuousMiningPulsesTheMarks() {
        int swing = 4;
        assertTrue(CLOSE_TICKS < swing, "open again before the next swing of a held attack starts");
        float low = 1, high = 0;
        for (float t = 0; t < 3 * swing; t += 0.25F) {
            float k = closeIn(t % swing);
            low = Math.min(low, k);
            high = Math.max(high, k);
        }
        assertEquals(0, low, 1.0E-6);
        assertEquals(1, high, 1.0E-6);
    }

    @Test
    void aClickOutOfReachFlashesAndDiesOut() {
        assertEquals(1, miss(0), 1.0E-6);
        assertEquals(1, miss(MISS_TICKS / 2), 1.0E-6, "full for the first half");
        assertTrue(miss(MISS_TICKS - 2) > 0 && miss(MISS_TICKS - 2) < 1, "fading");
        assertEquals(0, miss(MISS_TICKS));
        assertEquals(0, miss(1000), "no click yet");
        assertEquals(0, missShake(0), 1.0E-6);
        assertEquals(0, missShake(MISS_TICKS));
        for (float age = 0; age < MISS_TICKS; age += 0.5F) assertTrue(missShake(age) >= 0 && missShake(age) <= 1, "at " + age);
        assertTrue(missShake(MISS_TICKS / 4) > 0.5F, "the first beat");
    }

    @Test
    void aCuesWordFades() {
        assertEquals(1, cueText(0));
        assertEquals(1, cueText(CUE_TEXT_TICKS - CUE_TEXT_FADE), 1.0E-6);
        assertTrue(cueText(CUE_TEXT_TICKS - 2) > 0 && cueText(CUE_TEXT_TICKS - 2) < 1);
        assertEquals(0, cueText(CUE_TEXT_TICKS));
    }

    @Test
    void theThrowHintNamesTheGrabKeyWhenAPlainAttackWouldUseTheItem() {
        Component grab = Component.literal("R"), attack = Component.literal("Left Button");
        assertEquals("Left Button", throwKeys(grab, attack, GrabRule.throwsOnAttack(true, false)).getString(), "empty main hand");
        assertEquals("R + Left Button", throwKeys(grab, attack, GrabRule.throwsOnAttack(false, false)).getString(), "an item in the main hand");
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
