package dev.tentaworks.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.Predicate;

import static dev.tentaworks.client.ActuatorClearance.*;
import static org.junit.jupiter.api.Assertions.*;

class ActuatorClearanceTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Predicate<Vec3> FLOOR = p -> p.y < 0;
    private static final Predicate<Vec3> CEILING = p -> p.y > 2 || p.y < 0;

    private static Vec3 random(Random r) {
        return new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
    }

    @Test
    void intoLeavesAnAxisThatAlreadyGoesIntoTheFace() {
        Vec3 a = new Vec3(0.3, -0.8, 0.2).normalize();
        Vec3 got = into(a, UP, MIN_INTO);
        assertEquals(0, got.distanceTo(a), 1.0E-12);
    }

    @Test
    void intoTurnsAnAxisOutOfTheFaceJustEnoughAndKeepsItsHeading() {
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            Vec3 a = random(r), n = random(r);
            Vec3 got = into(a, n, MIN_INTO);
            assertEquals(1, got.length(), 1.0E-9);
            assertTrue(got.dot(n) <= -MIN_INTO + 1.0E-9, "goes into the face: " + got.dot(n));
            if (a.dot(n) > -MIN_INTO) {
                assertEquals(-MIN_INTO, got.dot(n), 1.0E-9, "no steeper than needed");
                Vec3 ta = a.subtract(n.scale(a.dot(n))), tg = got.subtract(n.scale(got.dot(n)));
                if (ta.length() > 1.0E-3) assertEquals(1, ta.normalize().dot(tg.normalize()), 1.0E-9, "same heading along the face");
            }
        }
    }

    @Test
    void intoIsContinuousAtTheLimit() {
        Vec3 n = UP;
        for (double t = -0.6; t <= -0.3; t += 0.001) {
            Vec3 a = new Vec3(Math.sqrt(1 - t * t), t, 0), b = new Vec3(Math.sqrt(1 - (t + 0.001) * (t + 0.001)), t + 0.001, 0);
            assertTrue(into(a, n, MIN_INTO).distanceTo(into(b, n, MIN_INTO)) < 0.01);
        }
    }

    @Test
    void aStrikeOnAFaceKeepsTheToolAndItsGripOutsideTheBlockThroughTheWholeSwing() {
        Random r = new Random(11);
        for (int i = 0; i < 400; i++) {
            Vec3 n = random(r), target = new Vec3(r.nextDouble(), r.nextDouble(), r.nextDouble());
            ActuatorPose.Swing plane = onFace(ActuatorPose.Swing.of(random(r), random(r)), n, MIN_INTO);
            assertTrue(plane.impact().dot(n) <= -MIN_INTO + 1.0E-9);
            assertEquals(0, plane.impact().dot(plane.back()), 1.0E-9);
            assertTrue(plane.back().dot(n) >= -1.0E-9, "rears back away from the face: " + plane.back().dot(n));
            for (double p = 0; p <= 1; p += 0.02) {
                ActuatorPose.Tool t = ActuatorPose.strikeAt(target, plane, 0.8, p, Vec3.ZERO, 0);
                for (double along = 0; along <= 0.8; along += 0.1)
                    assertTrue(t.head(along).subtract(target).dot(n) >= -1.0E-9, "tool " + along + " outside at swing " + p);
            }
            ActuatorPose.Tool impact = ActuatorPose.strikeAt(target, plane, 0.8, ActuatorPose.STRIKE_IMPACT, Vec3.ZERO, 0);
            assertEquals(0, impact.head(0.8).distanceTo(target), 1.0E-9, "the head lands on the contact");
            assertTrue(impact.grip().subtract(target).dot(n) >= 0.8 * MIN_INTO - 1.0E-9, "the grip stands off the face");
        }
    }

    @Test
    void aStrikePlaneThatAlreadyGoesIntoTheFaceIsKept() {
        ActuatorPose.Swing plane = ActuatorPose.Swing.of(new Vec3(0, -0.9, 0.4), new Vec3(0, 1, 0.3));
        ActuatorPose.Swing got = onFace(plane, UP, MIN_INTO);
        assertEquals(0, got.impact().distanceTo(plane.impact()), 1.0E-9);
        assertEquals(0, got.back().distanceTo(plane.back()), 1.0E-9);
    }

    @Test
    void aContactSitsOffTheFaceAndKeepsItsNudgeAlongIt() {
        Vec3 hit = new Vec3(3.5, 0, 0.5), nudge = new Vec3(0.1, -0.2, 0.05);
        Vec3 got = onFace(hit, nudge, UP);
        assertEquals(FACE_CLEAR, got.y, 1.0E-12);
        assertEquals(3.6, got.x, 1.0E-12);
        assertEquals(0.55, got.z, 1.0E-12);
        Vec3 wall = onFace(new Vec3(2, 1, 0.5), new Vec3(0.3, 0.1, 0), new Vec3(-1, 0, 0));
        assertEquals(2 - FACE_CLEAR, wall.x, 1.0E-12);
        assertEquals(1.1, wall.y, 1.0E-12);
    }

    @Test
    void anItemThatWouldReachIntoAFaceTiltsJustEnoughToStayOffIt() {
        Random r = new Random(5);
        Vec3 down = new Vec3(0, -1, 0);
        for (int i = 0; i < 2000; i++) {
            Vec3 a = random(r);
            double gap = r.nextDouble() * 0.8, len = 0.2 + r.nextDouble() * 0.6;
            Vec3 got = tilt(a, down, gap, len);
            assertEquals(1, got.length(), 1.0E-9);
            assertTrue(len * got.y <= Math.max(gap - ITEM_CLEAR, -len) + 1.0E-9, "the far end stays off the ceiling");
            if (len * a.y <= gap - ITEM_CLEAR) assertSame(a, got, "an item already clear is left alone");
            else {
                assertEquals(Math.max(gap - ITEM_CLEAR, -len), len * got.y, 1.0E-9, "no more tilt than needed");
                Vec3 ta = new Vec3(a.x, 0, a.z), tg = new Vec3(got.x, 0, got.z);
                if (ta.length() > 1.0E-3 && tg.length() > 1.0E-3) assertEquals(1, ta.normalize().dot(tg.normalize()), 1.0E-9, "same heading");
            }
        }
    }

    @Test
    void rotateTurnsAboutTheAxisKeepingLength() {
        Vec3 axis = new Vec3(0, 0, 1);
        Vec3 got = rotate(new Vec3(1, 0, 0), axis, Math.PI / 2);
        assertEquals(0, got.distanceTo(new Vec3(0, 1, 0)), 1.0E-12);
        Vec3 v = new Vec3(0.3, -0.7, 2);
        assertEquals(v.length(), rotate(v, new Vec3(1, 1, 0).normalize(), 1.1).length(), 1.0E-12);
        assertEquals(0, rotate(v, axis, 2 * Math.PI).distanceTo(v), 1.0E-12);
    }

    @Test
    void aFaceNormalTurnsToANewFaceAtTheChaseRateEvenFromAcross() {
        Vec3 down = new Vec3(0, -1, 0), east = new Vec3(1, 0, 0);
        Vec3 half = turnNormal(UP, east, 0.5);
        assertEquals(1, half.length(), 1.0E-12);
        assertEquals(half.x, half.y, 1.0E-12);
        assertEquals(0, turnNormal(UP, east, 1).distanceTo(east), 1.0E-12);
        Vec3 n = UP;
        double last = n.dot(down);
        for (int i = 0; i < 40; i++) {
            n = turnNormal(n, down, 0.2);
            assertEquals(1, n.length(), 1.0E-9);
            assertTrue(n.dot(down) > last, "the ceiling after the floor: turns a little every step, no stall on the old side");
            last = n.dot(down);
        }
        assertEquals(0, n.distanceTo(down), 1.0E-3);
        assertEquals(Math.cos(0.2 * Math.PI), turnNormal(UP, down, 0.2).dot(UP), 1.0E-9, "a step is a share of the angle");
        assertEquals(down, turnNormal(null, down, 0.1));
        assertNull(turnNormal(UP, null, 0.1));
    }

    @Test
    void turnTowardTakesTheShortWayRound() {
        assertEquals(Math.PI - 0.1 + 0.1, turnToward(Math.PI - 0.1, -Math.PI + 0.1, 0.5), 1.0E-9);
        assertEquals(0.5, turnToward(0, 1, 0.5), 1.0E-9);
        assertEquals(1, turnToward(0, 1, 2), 1.0E-9);
    }

    @Test
    void aTurnedChainStaysRigidAndEndsOnItsWrist() {
        Vec3 s = new Vec3(0, 1.3, 0), w = new Vec3(0.4, 0.2, 2.6);
        for (double turn : BENDS) {
            ActuatorPose.Chain c = ActuatorClearance.chain(s, w, new Vec3(1, 0.25, 0), new Vec3(1, 0.3, 0), 1.2, turn);
            assertEquals(0, c.wrist().distanceTo(w), 1.0E-6);
            for (int j = 1; j < c.joints().length; j++) assertEquals(1.2, c.joints()[j].distanceTo(c.joints()[j - 1]), 1.0E-9);
        }
    }

    @Test
    void aClearChainKeepsItsUsualBend() {
        Vec3 s = new Vec3(0, 1.3, 0), w = new Vec3(0.3, 0.4, 2.6);
        assertEquals(0, bend(s, w, new Vec3(1, 0.25, 0), new Vec3(1, 0.3, 0), 1.0, 0, FLOOR));
        assertEquals(0, bend(s, w, new Vec3(1, 0.25, 0), new Vec3(1, 0.3, 0), 1.0, 1.2, FLOOR), "back to the usual bend once it is clear");
    }

    @Test
    void aChainBulgingIntoTheFloorArchesOverIt() {
        Vec3 s = new Vec3(0, 1.3, 0), w = new Vec3(0, 0.3, 2.6), bulge = new Vec3(0, -1, 0);
        double seg = ActuatorPose.segment(s.distanceTo(w), 0.75, 3);
        assertTrue(inside(ActuatorClearance.chain(s, w, bulge, new Vec3(1, 0, 0), seg, 0), FLOOR) > 0, "the usual bend dips under the floor");
        double turn = bend(s, w, bulge, new Vec3(1, 0, 0), seg, 0, FLOOR);
        assertNotEquals(0, turn);
        assertEquals(0, inside(ActuatorClearance.chain(s, w, bulge, new Vec3(1, 0, 0), seg, turn), FLOOR));
    }

    @Test
    void underALowCeilingTheBendGoesSidewaysAndKeepsAClearTurn() {
        Vec3 s = new Vec3(0.25, 1.5, 0.3), w = new Vec3(0.9, 1.7, -0.4), bulge = new Vec3(0.45, 1, 0.35);
        double seg = ActuatorPose.segment(s.distanceTo(w), 0.6, 3);
        assertTrue(inside(ActuatorClearance.chain(s, w, bulge, new Vec3(1, 0.3, 0), seg, 0), CEILING) > 0, "the usual bend goes into the ceiling");
        double turn = bend(s, w, bulge, new Vec3(1, 0.3, 0), seg, 0, CEILING);
        assertEquals(0, inside(ActuatorClearance.chain(s, w, bulge, new Vec3(1, 0.3, 0), seg, turn), CEILING));
        assertEquals(turn, bend(s, w, bulge, new Vec3(1, 0.3, 0), seg, turn, CEILING), "a clear turn is kept, no flip between equals");
    }
}
