package dev.tentaworks.client;

import dev.tentaworks.client.ActuatorSight.Mark;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.Hold;
import dev.tentaworks.rig.HoldArm;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorHud.CAP_FADE;
import static dev.tentaworks.client.ActuatorHud.CAP_TICKS;
import static dev.tentaworks.client.ActuatorHud.CLOSE_PEAK;
import static dev.tentaworks.client.ActuatorHud.CLOSE_TICKS;
import static dev.tentaworks.client.ActuatorHud.GAUGE_HOLD;
import static dev.tentaworks.client.ActuatorHud.GAUGE_ROOMY;
import static dev.tentaworks.client.ActuatorHud.GAUGE_TICKS;
import static dev.tentaworks.client.ActuatorHud.GRIP_FROM;
import static dev.tentaworks.client.ActuatorHud.GRIP_GAP;
import static dev.tentaworks.client.ActuatorHud.MARK_GAP;
import static dev.tentaworks.client.ActuatorHud.MISS_TICKS;
import static dev.tentaworks.client.ActuatorHud.SETTLE_TICKS;
import static dev.tentaworks.client.ActuatorHud.acts;
import static dev.tentaworks.client.ActuatorHud.closeIn;
import static dev.tentaworks.client.ActuatorHud.cueCap;
import static dev.tentaworks.client.ActuatorHud.gauge;
import static dev.tentaworks.client.ActuatorHud.gaugeNotch;
import static dev.tentaworks.client.ActuatorHud.gaugeNotches;
import static dev.tentaworks.client.ActuatorHud.gaugePitch;
import static dev.tentaworks.client.ActuatorHud.grips;
import static dev.tentaworks.client.ActuatorHud.hintLine;
import static dev.tentaworks.client.ActuatorHud.hollow;
import static dev.tentaworks.client.ActuatorHud.lit;
import static dev.tentaworks.client.ActuatorHud.miss;
import static dev.tentaworks.client.ActuatorHud.missShake;
import static dev.tentaworks.client.ActuatorHud.settle;
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
    void theThrowHintNamesTheGrabKeyWhenAPlainAttackWouldUseTheItem() {
        Component grab = Component.literal("R"), attack = Component.literal("Left Button");
        assertEquals("Left Button", throwKeys(grab, attack, GrabRule.throwsOnAttack(true, false)).getString(), "empty main hand");
        assertEquals("R + Left Button", throwKeys(grab, attack, GrabRule.throwsOnAttack(false, false)).getString(), "an item in the main hand");
    }

    @Test
    void theHintLineNamesEachKeyThenWhatItDoes() {
        Component grab = Component.literal("V"), attack = Component.literal("Left Button");
        String empty = hintLine(grab, attack, true).getString(), tool = hintLine(grab, attack, false).getString();
        assertTrue(empty.matches("V \\S+   Left Button \\S+   V \\+ \\S \\S+"), empty);
        assertTrue(tool.contains("   V + Left Button "), tool);
        assertTrue(empty.indexOf("set_down") < empty.indexOf("throw") && empty.indexOf("throw") < empty.indexOf("distance"), empty);
    }

    @Test
    void theGrabKeyShowsForTheFirstSecondOfALook() {
        assertEquals(1, cueCap(0));
        assertEquals(1, cueCap(CAP_TICKS - CAP_FADE), 1.0E-6);
        assertTrue(cueCap(CAP_TICKS - 2) > 0 && cueCap(CAP_TICKS - 2) < 1, "fading");
        assertEquals(0, cueCap(CAP_TICKS));
        assertTrue(CAP_TICKS <= 30, "about a second");
    }

    @Test
    void aHoldingActuatorsBracketGrips() {
        boolean rightHanded = true, left = false, right = true;
        assertTrue(grips(HoldArm.OFF, rightHanded, left), "the off-hand Actuator of a right-handed wearer is the left one");
        assertFalse(grips(HoldArm.OFF, rightHanded, right));
        assertTrue(grips(HoldArm.MAIN, rightHanded, right));
        assertFalse(grips(HoldArm.MAIN, rightHanded, left));
        assertTrue(grips(HoldArm.OFF, !rightHanded, right), "left-handed: mirrored");
        assertTrue(grips(HoldArm.MAIN, !rightHanded, left));
        for (boolean side : new boolean[]{left, right}) {
            assertTrue(grips(HoldArm.BOTH, rightHanded, side), "a two-Actuator hold: both");
            assertFalse(grips(HoldArm.NONE, rightHanded, side));
        }
    }

    @Test
    void theFreeActuatorsBracketKeepsShowingAReach() {
        boolean rightHanded = true, left = false, right = true;
        assertTrue(acts(HoldArm.NONE, rightHanded, right), "the main-hand side acts");
        assertFalse(acts(HoldArm.NONE, rightHanded, left));
        assertTrue(acts(HoldArm.OFF, rightHanded, right), "the off-hand Actuator holds: the main-hand one still acts");
        assertTrue(acts(HoldArm.MAIN, rightHanded, left), "the main-hand Actuator holds: the other one acts");
        assertFalse(acts(HoldArm.MAIN, rightHanded, right));
        assertTrue(acts(HoldArm.NONE, !rightHanded, left), "left-handed: mirrored");
        for (HoldArm one : new HoldArm[]{HoldArm.MAIN, HoldArm.OFF})
            for (boolean side : new boolean[]{left, right})
                assertTrue(grips(one, rightHanded, side) != acts(one, rightHanded, side), "one bracket grips, the other reaches: " + one + " " + side);
    }

    @Test
    void theGripSnapsInSqueezesAndSettles() {
        assertEquals(1, settle(0), 1.0E-6, "out when the Grab lands");
        assertEquals(0, settle(SETTLE_TICKS));
        assertEquals(0, settle(-1), "no Grab yet");
        assertEquals(0, settle(1000));
        assertTrue(settle(3) <= 0.05F, "in within three ticks");
        float low = 1;
        for (float age = 0; age <= SETTLE_TICKS; age += 0.25F) {
            low = Math.min(low, settle(age));
            assertTrue(settle(age) <= 1 + 1.0E-6F, "at " + age);
        }
        assertTrue(low < -0.05F && low > -0.3F, "a small squeeze past the grip: " + low);
        assertTrue(GRIP_GAP + (GRIP_FROM - GRIP_GAP) * low >= 0, "never over the crosshair");
        assertTrue(GRIP_GAP < MARK_GAP, "tighter than a reaching bracket");
    }

    @Test
    void theGaugeHasANotchPerWheelStepFromTheNearestHoldToReach() {
        double pigMin = Hold.minDistance(0.9, 0), pigDefault = Hold.defaultDistance(0.9, 0);
        assertEquals(9, gaugeNotches(pigMin, 5), "0.95 to 5.0 in steps of 0.5: 0.95 .. 4.95");
        assertEquals(13, gaugeNotches(pigMin, 7));
        assertEquals(1, gaugeNotches(pigMin, pigMin), "no room: one tick");
        assertEquals(1, gaugeNotches(3, 2));
        int notches = gaugeNotches(pigMin, 5);
        int at = gaugeNotch(pigDefault, pigMin, notches);
        assertEquals(1, at, "the default hold sits one notch out");
        for (int n = -3; n <= 12; n++) {
            double moved = Hold.distance(pigDefault, n, 0.9, 0, 5);
            assertEquals(Math.max(0, Math.min(notches - 1, at + n)), gaugeNotch(moved, pigMin, notches), n + " notches of the wheel");
        }
        assertEquals(0, gaugeNotch(0, pigMin, notches), "clamped");
        assertEquals(notches - 1, gaugeNotch(99, pigMin, notches));
    }

    @Test
    void aLongGaugePacksItsTicks() {
        assertEquals(2, gaugePitch(9));
        assertEquals(2, gaugePitch(GAUGE_ROOMY));
        assertEquals(1, gaugePitch(GAUGE_ROOMY + 1));
        int longest = gaugeNotches(Hold.minDistance(0.3, 0), 16);
        assertTrue((longest - 1) * gaugePitch(longest) + 1 <= 33, "Reach 16 stays a small column: " + longest);
        assertTrue((GAUGE_ROOMY - 1) * 2 + 1 <= 33);
    }

    @Test
    void theGaugeLingersAfterTheKeyOrTheWheelAndFades() {
        assertEquals(1, gauge(0));
        assertEquals(1, gauge(GAUGE_HOLD), 1.0E-6);
        float mid = gauge((GAUGE_HOLD + GAUGE_TICKS) / 2);
        assertTrue(mid > 0 && mid < 1, "fading");
        assertEquals(0, gauge(GAUGE_TICKS));
        assertEquals(0, gauge(-1), "never in hand");
        assertEquals(0, gauge(1.0E9F));
    }

    @Test
    void theHintGoesUpForTheFirstTwoHoldsOfASession() {
        GrabHint hint = new GrabHint();
        assertTrue(hint.live(true));
        assertTrue(hint.landed(100, true, false));
        assertTrue(hint.landed(400, true, false));
        assertFalse(hint.live(true), "learned: the Grab key no longer shows beside the cue");
        for (int i = 0; i < 5; i++) assertFalse(hint.landed(1000 + 200 * i, true, false), "the third hold and later: nothing");
        assertEquals(GrabHint.HOLDS, hint.shown());
        hint.reset();
        assertTrue(hint.landed(5000, true, false), "a new session");
    }

    @Test
    void theHintNeverCoversARefusalLineAndIsNotSpentOnIt() {
        GrabHint hint = new GrabHint();
        assertFalse(hint.landed(100, true, true), "another action-bar line is up");
        assertEquals(0, hint.shown());
        assertTrue(hint.landed(200, true, false), "the next hold gets it");
        assertTrue(hint.landed(300, true, false));
        assertFalse(hint.landed(400, true, false));
    }

    @Test
    void theHintsCanBeTurnedOff() {
        GrabHint hint = new GrabHint();
        assertFalse(hint.live(false));
        assertFalse(hint.landed(100, false, false));
        assertEquals(0, hint.shown());
        assertTrue(hint.landed(200, true, false), "turned on again: nothing was spent");
    }

    @Test
    void theHintLineIsKeptUpOnceWhileTheHoldLastsAndNothingElseTookTheBar() {
        GrabHint hint = new GrabHint();
        assertTrue(hint.landed(100, true, false));
        int again = 100 + GrabHint.TICKS - GrabHint.BAR_TICKS;
        int renewals = 0;
        for (int t = 101; t < 400; t++) if (hint.renews(t, true, true)) renewals++;
        assertEquals(1, renewals);
        assertTrue(hint.renews(again, true, true));
        assertFalse(hint.renews(again, false, true), "set down or thrown by then");
        assertFalse(hint.renews(again, true, false), "a refusal line took the bar");
        assertTrue(GrabHint.TICKS - GrabHint.BAR_TICKS <= GrabHint.BAR_TICKS - 20, "renewed before the line has faded at all (the last 20 ticks of a line fade)");
    }
}
