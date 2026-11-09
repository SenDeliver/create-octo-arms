package dev.tentaworks.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorPose.*;
import static org.junit.jupiter.api.Assertions.*;

class ActuatorPoseTest {
    private static final int[] SIDES = {LEFT, RIGHT};

    private static Chain rest(int side, float t) {
        Vec3 s = shoulder(side), w = restWrist(side, t);
        return chain(s, w, restBulge(side), new Vec3(side, 0.3, 0), segment(s.distanceTo(w), restStraight(t), 4));
    }

    @Test
    void restPoseIsAboveTheShouldersAndWiderThanTheBody() {
        for (int side : SIDES) {
            for (float t = 0; t < 400; t += 3.7F) {
                Chain c = rest(side, t);
                Vec3 wrist = c.wrist();
                assertEquals(restWrist(side, t).x, wrist.x, 1.0E-6, "wrist reaches its rest point");
                assertEquals(restWrist(side, t).y, wrist.y, 1.0E-6);
                assertTrue(Math.abs(sideways(wrist)) > BODY_HALF_WIDTH, "wider than the body: " + sideways(wrist));
                assertTrue(heightAboveFeet(wrist) > HEAD_TOP, "above the head: " + heightAboveFeet(wrist));
                for (int j = 1; j < c.joints().length; j++) {
                    assertEquals(side, Math.signum(sideways(c.joints()[j])), "joint " + j + " on its own side");
                    assertTrue(c.joints()[j].y > shoulder(side).y, "joint " + j + " above the shoulder");
                }
                float open = restOpen(side, t);
                assertTrue(open >= 0 && open <= 1, "gripper opening " + open);
            }
        }
    }

    @Test
    void restPoseIsSymmetric() {
        for (float t = 0; t < 300; t += 2.9F) {
            Vec3[] l = rest(LEFT, t).joints(), r = rest(RIGHT, t).joints();
            for (int j = 0; j < l.length; j++) {
                assertEquals(1 - l[j].x, r[j].x, 1.0E-9, "mirrored x, joint " + j);
                assertEquals(l[j].y, r[j].y, 1.0E-9);
                assertEquals(l[j].z, r[j].z, 1.0E-9);
            }
        }
    }

    @Test
    void segmentsAreRigidAlongTheChain() {
        Vec3 s = new Vec3(0.2, 0.8, 0.4);
        Vec3[] targets = {new Vec3(0.3, 1.6, 0.4), new Vec3(1.5, 0.8, -2), new Vec3(0.2, 0.8, 0.4), new Vec3(0.2, 9, 0.4),
                new Vec3(0.2, -9, 0.4), new Vec3(0.2, 0.8, 6), new Vec3(-4, 3, -12), new Vec3(0.2, 0.8001, 0.4)};
        Vec3[] bulges = {new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0), Vec3.ZERO};
        for (Vec3 w : targets) {
            for (Vec3 bulge : bulges) {
                for (double seg : new double[]{MIN_SEGMENT, 0.3, 1.0, 5.5}) {
                    Chain c = chain(s, w, bulge, new Vec3(-1, 0.3, 0), seg);
                    assertEquals(SEGMENTS + 1, c.joints().length);
                    assertEquals(s, c.joints()[0]);
                    for (int j = 0; j < SEGMENTS; j++) {
                        Vec3 a = c.joints()[j], b = c.joints()[j + 1];
                        assertTrue(Double.isFinite(b.x) && Double.isFinite(b.y) && Double.isFinite(b.z), "finite " + w + " " + bulge);
                        assertEquals(seg, a.distanceTo(b), 1.0E-9, "segment " + j + " to " + w + " bulge " + bulge);
                    }
                    assertEquals(1, c.hinge().length(), 1.0E-9);
                }
            }
        }
    }

    @Test
    void chainEndsOnTheWristWithinReach() {
        Vec3 s = Vec3.ZERO;
        for (Vec3 w : new Vec3[]{new Vec3(0, 0, -0.5), new Vec3(0.4, 0.4, 0), new Vec3(0, -0.6, 0.2)}) {
            Chain c = chain(s, w, new Vec3(0, 1, 0), new Vec3(1, 0, 0), 0.3);
            assertEquals(0, c.wrist().distanceTo(w), 1.0E-6, "ends on " + w);
        }
    }

    @Test
    void reachIsClampedAndPointsAtTheTarget() {
        Vec3 s = Vec3.ZERO;
        double seg = 0.5, length = SEGMENTS * seg;
        for (Vec3 w : new Vec3[]{new Vec3(0, 0, -10), new Vec3(3, 4, 0), new Vec3(0, -50, 0), new Vec3(0, 0, -length * 0.95)}) {
            Chain c = chain(s, w, new Vec3(0, 1, 0), new Vec3(1, 0, 0), seg);
            Vec3 end = c.wrist();
            assertTrue(end.length() <= 0.985 * length + 1.0E-9, "never past 98.5% of the chain: " + end.length());
            assertTrue(end.length() > 0.9 * length, "nearly straight toward a far target");
            assertEquals(1, end.normalize().dot(w.normalize()), 1.0E-9, "points at the target");
        }
        double prev = 0;
        for (double d = 0; d < 3 * length; d += 0.01) {
            double r = softReach(d, length);
            assertTrue(r >= prev - 1.0E-12, "monotonic");
            prev = r;
        }
        assertEquals(0.3, segment(0.63, 0.7, 4), 1.0E-9);
        assertEquals(MIN_SEGMENT, segment(0, 0.7, 4));
        assertEquals(2, segment(100, 0.9, 2));
    }

    @Test
    void shouldersMirror() {
        assertEquals(0.5, (shoulderX(LEFT) + shoulderX(RIGHT)) / 2, 1.0E-9);
        assertTrue(shoulderX(LEFT) < 0.5 && shoulderX(RIGHT) > 0.5);
    }

    @Test
    void idleIsDeterministicBoundedAndCalm() {
        for (int side : SIDES) {
            for (float t = 0; t < 500; t += 1.3F) {
                assertEquals(restWrist(side, t), restWrist(side, t));
                double b = breathe(t), g = gesture(t, side);
                assertTrue(b >= -1 && b <= 1);
                assertTrue(g >= 0 && g <= 1);
                assertEquals(breathe(t), breathe(t + BREATH_TICKS), 1.0E-4);
                assertEquals(gesture(t, side), gesture(t + GESTURE_TICKS, side), 1.0E-4);
                assertTrue(restWrist(side, t).distanceTo(restWrist(side, t + 1)) < 0.01, "slow: under 0.01 block per tick");
            }
        }
        assertTrue(BREATH_TICKS >= 60 && BREATH_TICKS <= 80, "breath 3-4 s");
        boolean apart = true;
        for (float t = 0; t < GESTURE_TICKS; t += 0.5F) apart &= gesture(t, LEFT) == 0 || gesture(t, RIGHT) == 0;
        assertTrue(apart, "the two grippers never flex together");
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
    }

    @Test
    void levelsEaseWithoutJumps() {
        assertEquals(0, level(0, true, 0), 1.0E-9);
        assertEquals(1, level(0, true, REACH_TICKS), 1.0E-9);
        assertEquals(1, level(1, false, 0), 1.0E-9);
        assertEquals(0, level(1, false, RETURN_TICKS), 1.0E-9);
        for (float from = 0; from <= 1; from += 0.1F) {
            assertEquals(from, level(from, true, 0), 1.0E-6, "a flip starts where the level is");
            assertEquals(from, level(from, false, 0), 1.0E-6);
            double prev = from;
            for (float age = 0.1F; age < 12; age += 0.1F) {
                double up = level(from, true, age);
                assertTrue(up >= prev - 1.0E-9 && up <= 1, "rises steadily");
                prev = up;
            }
        }
        assertTrue(level(0, true, 1) > 1 - level(1, false, 1), "out fast (ease out), back slow (ease in)");
    }

    @Test
    void swingsStayBoundedAndReturn() {
        assertEquals(0, strike(0).length(), 1.0E-9);
        assertEquals(0, strike(1).length(), 1.0E-9);
        assertEquals(0, push(1), 1.0E-9);
        for (float sw = 0; sw <= 1; sw += 0.05F) {
            assertTrue(strike(sw).x >= 0 && strike(sw).x <= 0.35);
            assertTrue(push(sw) >= 0 && push(sw) <= 0.2);
        }
    }
}
