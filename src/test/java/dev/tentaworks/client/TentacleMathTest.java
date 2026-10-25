package dev.tentaworks.client;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step arcs, pad normals and elbow poles stay continuous where the old lerps flipped, and the arc keeps to the body's side. */
class TentacleMathTest {
    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0), EAST = new Vec3(1, 0, 0), WEST = new Vec3(-1, 0, 0);

    /** Floor to ceiling in a 2-high tunnel: the old normal lerp passed through zero and the tip jumped at mid-arc. */
    @Test
    void opposedFacesGiveAContinuousArcAndNormal() {
        Vec3 a = new Vec3(0.5, 10, 0.5), b = new Vec3(0.5, 12, 1.5), body = new Vec3(0.5, 11, -0.5);
        Vec3 prev = null, prevN = null;
        for (int i = 0; i <= 200; i++) {
            double e = i / 200.0;
            Vec3 p = TentacleMath.arc(a, b, body, UP, DOWN, e);
            Vec3 q = TentacleMath.liftAxis(UP, DOWN, a.add(b).scale(0.5), body);
            Vec3 n = TentacleMath.normalBetween(UP, DOWN, q, e);
            assertEquals(1, n.length(), 1.0E-9, "unit normal at " + e);
            if (prev != null) {
                assertTrue(p.distanceTo(prev) < 0.05, "tip step " + p.distanceTo(prev) + " at " + e);
                assertTrue(n.distanceTo(prevN) < 0.05, "normal step " + n.distanceTo(prevN) + " at " + e);
            }
            prev = p;
            prevN = n;
        }
        assertEquals(a, TentacleMath.arc(a, b, body, UP, DOWN, 0));
        assertEquals(b, TentacleMath.arc(a, b, body, UP, DOWN, 1));
        Vec3 midN = TentacleMath.normalBetween(UP, DOWN, EAST, 0.5);
        assertEquals(EAST, midN, "swings through the lift axis, never zero");
    }

    /** Stepping around an outer corner: the chord passes through the block; the arc pulls toward the body instead. */
    @Test
    void theArcStaysOnTheBodySideOfTheChord() {
        Vec3 a = new Vec3(1.0, 10, 0.5), b = new Vec3(0.5, 10, 1.0), body = new Vec3(2, 10.5, 2);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toBody = body.subtract(mid).normalize();
        for (int i = 1; i < 20; i++) {
            Vec3 p = TentacleMath.arc(a, b, body, EAST, new Vec3(0, 0, 1), i / 20.0);
            assertTrue(p.subtract(mid).dot(toBody) > 0, "on the body's side at " + i / 20.0);
        }
        Vec3 peak = TentacleMath.arc(a, b, body, EAST, new Vec3(0, 0, 1), 0.5);
        Vec3 lift = TentacleMath.normalBetween(EAST, new Vec3(0, 0, 1), toBody, 0.5);
        assertTrue(peak.subtract(mid).dot(lift) > 0.3, "lifted off the faces");
        assertTrue(peak.subtract(mid).dot(toBody) > 0.3, "pulled toward the body");
        assertTrue(peak.distanceTo(body) > TentacleMath.BODY_CLEAR * 0.5, "but not into it");
    }

    /** The arc never crosses the wearer: Activation from the free pose and a floor-to-ceiling Step in a 2-high tunnel both pass wide of the body. */
    @Test
    void theArcKeepsClearOfTheBody() {
        Vec3 body = new Vec3(0.5, 11, 0.5);
        Vec3[][] cases = {
                {new Vec3(-0.5, 10.3, 0.2), new Vec3(2.5, 10, 2.5), null, UP},
                {new Vec3(0.9, 10, 0.5), new Vec3(0.9, 12, 0.5), UP, DOWN},
                {new Vec3(-1.5, 11, 0.5), new Vec3(2.5, 11, 0.5), EAST, WEST},
        };
        for (Vec3[] c : cases) {
            double closest = Double.MAX_VALUE;
            for (int i = 0; i <= 100; i++) closest = Math.min(closest, TentacleMath.arc(c[0], c[1], body, c[2], c[3], i / 100.0).distanceTo(body));
            assertTrue(closest > 0.35, "closest approach " + closest + " for " + c[0] + " -> " + c[1]);
        }
    }

    /** A first Step from the free pose has no start normal: it lifts along the target's face. */
    @Test
    void noStartNormalLiftsAlongTheTargetFace() {
        Vec3 a = new Vec3(0, 10, 0), b = new Vec3(2, 10, 0), body = new Vec3(1, 11, 0);
        Vec3 peak = TentacleMath.arc(a, b, body, null, UP, 0.5);
        assertTrue(peak.y > 10.5, "lifted above the floor");
        assertEquals(1, peak.x, 1.0E-9);
    }

    /** The body on the chord (a 2-high tunnel, mid-arc at body height): the lift axis still exists and crosses the face. */
    @Test
    void liftAxisWhenTheBodySitsOnTheChord() {
        Vec3 q = TentacleMath.liftAxis(UP, DOWN, new Vec3(0.5, 11, 0.5), new Vec3(0.5, 11, 0.5));
        assertEquals(1, q.length(), 1.0E-9);
        assertEquals(0, q.dot(UP), 1.0E-9);
    }

    /** The old pole projected to nothing when the arm lined up with its hint and then snapped to a fallback; now it fades. */
    @Test
    void poleFadesInsteadOfSnapping() {
        Vector3f hint = new Vector3f(1.5F, 0.3F, 0).normalize();
        Vector3f alt1 = new Vector3f(0, 0, 1), alt2 = new Vector3f(0, 1, 0);
        Vector3f prev = null;
        for (int i = -40; i <= 40; i++) {
            float ang = i * 0.01F;
            Vector3f u = new Vector3f(hint).rotateZ(ang).normalize();
            Vector3f p = TentacleMath.pole(u, hint, alt1, alt2);
            assertEquals(1, p.length(), 1.0E-5F);
            assertEquals(0, p.dot(u), 1.0E-5F, "perpendicular to the arm");
            if (prev != null) assertTrue(p.distance(prev) < 0.15F, "pole step " + p.distance(prev) + " at " + ang);
            prev = p;
        }
        Vector3f exact = TentacleMath.pole(hint, hint, alt1, alt2);
        assertEquals(0, exact.distance(alt1), 1.0E-5F, "lined up: the first alternative");
        Vector3f both = TentacleMath.pole(alt1, alt1, alt1, alt2);
        assertEquals(0, both.distance(alt2), 1.0E-5F, "both lined up: the second");
    }

    @Test
    void padSideIsAWorldAxisOnTheFace() {
        Vector3f up = new Vector3f(0, 1, 0), x = new Vector3f(1, 0, 0);
        assertEquals(0, TentacleMath.padSide(new Vector3f(1, 0, 0), up, x).distance(up), 1.0E-6F, "wall: up");
        assertEquals(0, TentacleMath.padSide(up, up, x).distance(x), 1.0E-6F, "floor: world x");
        Vector3f prev = null;
        for (int i = 0; i <= 90; i++) {
            Vector3f n = new Vector3f(0, 1, 0).rotateX(i * (float) Math.PI / 180);
            Vector3f side = TentacleMath.padSide(n, up, x);
            assertNotNull(side, "tilt " + i);
            assertEquals(0, side.dot(n), 1.0E-5F, "on the face at tilt " + i);
            if (prev != null) assertTrue(side.distance(prev) < 0.1F, "side step " + side.distance(prev) + " at tilt " + i);
            prev = side;
        }
        assertNotNull(TentacleMath.padSide(new Vector3f(0.6F, 0.8F, 0).normalize(), up, x));
    }

    @Test
    void lightLerpsEachChannel() {
        int dark = LightTexture.pack(0, 0), lit = LightTexture.pack(15, 5);
        assertEquals(LightTexture.pack(8, 3), TentacleMath.lerpLight(dark, lit, 0.5F));
        assertEquals(lit, TentacleMath.lerpLight(lit, lit, 0.3F));
        assertEquals(dark, TentacleMath.lerpLight(dark, lit, 0));
        assertEquals(lit, TentacleMath.lerpLight(dark, lit, 1));
    }

    /** The first-person cut trims the part of a segment near the eye instead of dropping the whole piece. */
    @Test
    void sphereCutTrimsOneEnd() {
        Vector3f a = new Vector3f(0, 0, 0), b = new Vector3f(2, 0, 0);
        float[] cut = TentacleMath.outsideSphere(a, b, 0.5F);
        assertEquals(0.25F, cut[0], 1.0E-6F, "starts where the segment leaves the sphere");
        assertEquals(1, cut[1], 1.0E-6F);
        cut = TentacleMath.outsideSphere(b, a, 0.5F);
        assertEquals(0, cut[0], 1.0E-6F);
        assertEquals(0.75F, cut[1], 1.0E-6F);
        assertNull(TentacleMath.outsideSphere(new Vector3f(-0.1F, 0, 0), new Vector3f(0.1F, 0, 0), 0.5F), "swallowed");
        float[] miss = TentacleMath.outsideSphere(new Vector3f(0, 1, 0), new Vector3f(2, 1, 0), 0.5F);
        assertEquals(0, miss[0], 1.0E-6F);
        assertEquals(1, miss[1], 1.0E-6F);
        assertNull(TentacleMath.outsideSphere(new Vector3f(-2, 0, 0), new Vector3f(2, 0, 0), 0.5F), "cut in the middle: dropped as before");
    }

    /** The free Tentacle's spring settles without overshoot and does not blow up at any tick size. */
    @Test
    void springSettlesWithoutOvershoot() {
        Vector3f x = new Vector3f(0, 0, 0), v = new Vector3f(), goal = new Vector3f(1, 0, 0);
        float prev = 0;
        for (int i = 0; i < 40; i++) {
            TentacleMath.spring(x, v, goal, 3, 1);
            assertTrue(x.x >= prev - 1.0E-6F && x.x <= 1 + 1.0E-6F, "monotone, no overshoot: " + x.x);
            prev = x.x;
        }
        assertEquals(1, x.x, 1.0E-3F);
        x.set(0, 0, 0);
        v.set(0, 0, 0);
        TentacleMath.spring(x, v, goal, 3, 50);
        assertEquals(1, x.x, 1.0E-3F, "a huge tick lands on the goal");
    }
}
