package dev.tentaworks.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorPose.*;
import static org.junit.jupiter.api.Assertions.*;

class ActuatorPoseTest {
    private static final int[] SIDES = {LEFT, RIGHT};

    @Test
    void restPoseIsAboveTheShouldersAndWiderThanTheBody() {
        for (int side : SIDES) {
            for (float t = 0; t < 400; t += 3.7F) {
                Vec3 tip = restTip(side, t);
                assertEquals(side, Math.signum(sideways(tip)), "tip on its own side");
                assertTrue(Math.abs(sideways(tip)) > BODY_HALF_WIDTH, "wider than the body: " + sideways(tip));
                assertTrue(heightAboveFeet(tip) > HEAD_TOP, "above the head: " + heightAboveFeet(tip));
                assertTrue(tip.y > shoulder(side).y, "above the shoulder");
                Vec3 elbow = restElbow(side, t);
                assertEquals(side, Math.signum(sideways(elbow)));
                assertTrue(elbow.y > shoulder(side).y && elbow.y < tip.y, "elbow between shoulder and tip height");
                float open = restOpen(side, t);
                assertTrue(open >= 0 && open <= 1, "claw opening " + open);
            }
        }
    }

    @Test
    void shouldersMirror() {
        assertEquals(0.5, (shoulderX(LEFT) + shoulderX(RIGHT)) / 2, 1.0E-9);
        assertTrue(shoulderX(LEFT) < 0.5 && shoulderX(RIGHT) > 0.5);
    }

    @Test
    void idleIsDeterministicBoundedAndOutOfPhase() {
        for (int side : SIDES) {
            for (float t = 0; t < 500; t += 1.3F) {
                assertEquals(breathe(t, side), breathe(t, side));
                assertEquals(restTip(side, t), restTip(side, t));
                double b = breathe(t, side), g = gesture(t, side);
                assertTrue(b >= -1 && b <= 1);
                assertTrue(g >= 0 && g <= 1);
                assertEquals(breathe(t, side), breathe(t + BREATH_TICKS, side), 1.0E-4);
                assertEquals(gesture(t, side), gesture(t + GESTURE_TICKS, side), 1.0E-4);
            }
        }
        assertNotEquals(breathe(10, LEFT), breathe(10, RIGHT), 1.0E-3);
        boolean apart = true;
        for (float t = 0; t < GESTURE_TICKS; t += 0.5F) apart &= gesture(t, LEFT) == 0 || gesture(t, RIGHT) == 0;
        assertTrue(apart, "the two Actuators never gesture together");
    }

    @Test
    void gestureIsMostlyStill() {
        int moving = 0, samples = 0;
        for (float t = 0; t < GESTURE_TICKS; t += 1, samples++) if (gesture(t, LEFT) > 0) moving++;
        assertTrue(moving <= GESTURE_LENGTH + 1 && moving > 0, "moving ticks " + moving + "/" + samples);
    }

    @Test
    void firstPersonEntryIsOutsideTheFrameOnItsOwnSide() {
        double[] aspects = {4.0 / 3, 16.0 / 9, 21.0 / 9};
        for (double fov = 30; fov <= 110; fov += 5) {
            for (double aspect : aspects) {
                for (int side : SIDES) {
                    Vec3 p = entry(side, fov, aspect);
                    assertFalse(inFrame(p, fov, aspect), "fov " + fov + " aspect " + aspect + " side " + side + ": " + p);
                    assertEquals(side, Math.signum(p.x));
                    assertTrue(p.y < 0, "below the eye");
                    assertEquals(ENTRY_DISTANCE, Math.hypot(p.x, p.z), 1.0E-9);
                }
            }
        }
    }

    @Test
    void inFrameSeesTheCentre() {
        assertTrue(inFrame(new Vec3(0, 0, 1), 70, 16.0 / 9));
        assertFalse(inFrame(new Vec3(0, 0, -1), 70, 16.0 / 9));
    }

    @Test
    void opacityIsClamped() {
        assertEquals(MIN_OPACITY, opacity(0));
        assertEquals(MIN_OPACITY, opacity(-3));
        assertEquals(1, opacity(2));
        assertEquals(0.5F, opacity(0.5), 1.0E-6);
        assertEquals(1, opacity(Double.NaN));
        assertEquals(1, opacity(Double.POSITIVE_INFINITY));
        assertEquals(255, alpha(1));
        assertEquals(Math.round(MIN_OPACITY * 255), alpha(0));
    }

    @Test
    void timingCurvesStayInRangeAndSettle() {
        for (float age = -2; age < 30; age += 0.25F) {
            double w = windUp(age), f = followThrough(age), fo = folded(age), fl = flexOpen(age);
            assertTrue(w >= 0 && w <= 1);
            assertTrue(f >= 0 && f <= 1);
            assertTrue(fo >= 0 && fo <= 1);
            assertTrue(fl >= 0 && fl <= 1);
        }
        assertEquals(0, windUp(WIND_UP_TICKS));
        assertEquals(0, followThrough(FOLLOW_TICKS));
        assertEquals(1, folded(0), 1.0E-9);
        assertEquals(0, folded(FLEX_TICKS));
        assertEquals(0, flexOpen(FLEX_TICKS));
        assertEquals(1, approach(0.9F, true, 4));
        assertEquals(0, approach(0.1F, false, 4));
    }

    @Test
    void reachIsClampedAndElbowArcsOut() {
        Vec3 s = Vec3.ZERO, tip = new Vec3(0, 0, -10);
        assertEquals(4, clampReach(s, tip, 4).length(), 1.0E-9);
        assertEquals(new Vec3(0, 0, -2), clampReach(s, new Vec3(0, 0, -2), 4));
        Vec3 e = elbow(s, new Vec3(0, 0, -3), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
        assertTrue(e.x > 0 && e.y > 0, "elbow pushed out and up: " + e);
        assertEquals(s, elbow(s, s, new Vec3(1, 0, 0), new Vec3(0, 1, 0)));
    }
}
