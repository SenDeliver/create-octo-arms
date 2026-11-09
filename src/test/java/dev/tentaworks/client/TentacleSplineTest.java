package dev.tentaworks.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TentacleSplineTest {
    private static final Vector3f S = new Vector3f(0.17F, 0.62F, 0.47F);
    private static final Vector3f E = new Vector3f(-0.9F, 0.9F, 0.2F);
    private static final Vector3f W = new Vector3f(-1.8F, -0.4F, 0.6F);

    @Test
    void passesThroughShoulderElbowWrist() {
        TentacleSpline.Ring[] r = TentacleSpline.sample(S, E, W, new Vector3f(-1, 0, 0), new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), 24);
        assertEquals(24, r.length);
        assertEquals(0, r[0].c().distance(S), 1.0E-5);
        assertEquals(0, r[r.length - 1].c().distance(W), 1.0E-5);
        float best = Float.MAX_VALUE;
        for (TentacleSpline.Ring ring : r) best = Math.min(best, ring.c().distance(E));
        assertTrue(best < 1.0E-4, "elbow on the curve, closest " + best);
    }

    @Test
    void framesAreOrthonormalAndTwistFree() {
        TentacleSpline.Ring[] r = TentacleSpline.sample(S, E, W, new Vector3f(-1, 0, 0), new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), 40);
        TentacleSpline.Ring prev = null;
        for (TentacleSpline.Ring ring : r) {
            assertEquals(1, ring.y().length(), 1.0E-4);
            assertEquals(1, ring.x().length(), 1.0E-4);
            assertEquals(1, ring.z().length(), 1.0E-4);
            assertEquals(0, ring.x().dot(ring.y()), 1.0E-4);
            assertEquals(0, ring.z().dot(ring.y()), 1.0E-4);
            assertTrue(ring.c().isFinite());
            if (prev != null) {
                assertTrue(ring.along() >= prev.along());
                assertTrue(ring.x().dot(prev.x()) > 0.8, "side axis snapped: " + ring.x().dot(prev.x()));
            }
            prev = ring;
        }
        assertEquals(2, r[r.length - 1].seg(), 1.0E-5);
    }

    @Test
    void arrivesAlongWristDirection() {
        Vector3f in = new Vector3f(0, -1, 0);
        TentacleSpline.Ring[] r = TentacleSpline.sample(S, E, W, null, in, null, 60);
        TentacleSpline.Ring last = r[r.length - 1];
        assertTrue(last.y().dot(in) > 0.95, "wrist tangent follows the face normal: " + last.y().dot(in));
    }

    @Test
    void degenerateInputsStayFinite() {
        TentacleSpline.Ring[] r = TentacleSpline.sample(S, new Vector3f(S), new Vector3f(S), null, null, null, 8);
        for (TentacleSpline.Ring ring : r) {
            assertTrue(ring.c().isFinite());
            assertTrue(ring.y().isFinite() && ring.x().isFinite());
        }
        TentacleSpline.Ring[] two = TentacleSpline.sample(S, E, W, null, null, null, 1);
        assertEquals(2, two.length);
    }
}
