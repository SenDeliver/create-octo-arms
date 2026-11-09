package dev.tentaworks.client;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The fluid centre line of a drawn Tentacle: two cubic Hermite pieces, shoulder to elbow and elbow to wrist, that pass through all
 * three points. The shoulder tangent leaves the tank sideways, the elbow tangent is the chord's, the wrist tangent comes in along
 * the face normal, so the arm meets a Grip square on. Sampled into rings with twist-free frames (parallel transport).
 */
public final class TentacleSpline {
    /** Sampled ring: centre, unit tangent, unit side axis (x) and the second axis (z); {@code along} is the arc length so far. */
    public record Ring(Vector3f c, Vector3f y, Vector3f x, Vector3f z, float along, float seg) {
    }

    private TentacleSpline() {
    }

    /**
     * Samples the curve shoulder {@code s} → elbow {@code e} → wrist {@code w} into {@code n} rings ({@code n >= 2}).
     * {@code shoulderDir} is the direction the arm leaves the shoulder in, {@code wristDir} the direction it arrives at the wrist
     * (both unit, may be null to use the chords), {@code xHint} the first frame's side axis. {@code seg} of a ring is 0..1 along
     * the first piece and 1..2 along the second (for lighting).
     */
    public static Ring[] sample(Vector3f s, Vector3f e, Vector3f w, Vector3f shoulderDir, Vector3f wristDir, Vector3f xHint, int n) {
        n = Math.max(2, n);
        Vector3f d1 = new Vector3f(e).sub(s), d2 = new Vector3f(w).sub(e);
        float l1 = d1.length(), l2 = d2.length();
        Vector3f chord = new Vector3f(w).sub(s);
        Vector3f tE = chord.lengthSquared() < 1.0E-8F ? new Vector3f(d1) : new Vector3f(chord).normalize().mul((l1 + l2) * 0.5F);
        Vector3f tS = tangent(shoulderDir, d1, l1, 0.9F);
        Vector3f tW = tangent(wristDir, d2, l2, 0.9F);
        // split rings by piece length, at least 2 per piece when both exist
        int n1 = l1 + l2 < 1.0E-6F ? n / 2 : Math.round(n * l1 / (l1 + l2));
        n1 = Mth.clamp(n1, l1 < 1.0E-6F ? 0 : 1, n - (l2 < 1.0E-6F ? 0 : 1));
        Ring[] out = new Ring[n];
        Vector3f prevC = null, prevX = null;
        float along = 0;
        for (int i = 0; i < n; i++) {
            float seg;
            Vector3f c, t;
            if (i < n1) {
                float u = n1 <= 1 ? 0 : (float) i / n1;
                c = hermite(s, tS, e, tE, u);
                t = hermiteTangent(s, tS, e, tE, u);
                seg = u;
            } else {
                float u = n - 1 - n1 <= 0 ? 1 : (float) (i - n1) / (n - 1 - n1);
                c = hermite(e, tE, w, tW, u);
                t = hermiteTangent(e, tE, w, tW, u);
                seg = 1 + u;
            }
            if (t.lengthSquared() < 1.0E-10F) t = prevC == null ? new Vector3f(d1.lengthSquared() > 1.0E-10F ? d1 : new Vector3f(0, 1, 0)) : new Vector3f(c).sub(prevC);
            if (t.lengthSquared() < 1.0E-10F) t = new Vector3f(0, 1, 0);
            t.normalize();
            Vector3f x;
            if (prevX == null) {
                x = perpendicular(xHint, t);
            } else {
                x = perpendicular(prevX, t);
            }
            Vector3f z = new Vector3f(x).cross(t).normalize();
            if (prevC != null) along += c.distance(prevC);
            out[i] = new Ring(c, t, x, z, along, seg);
            prevC = c;
            prevX = x;
        }
        return out;
    }

    private static Vector3f tangent(Vector3f dir, Vector3f chord, float len, float scale) {
        if (dir == null || dir.lengthSquared() < 1.0E-8F || len < 1.0E-6F) return new Vector3f(chord);
        return new Vector3f(dir).normalize().mul(len * scale);
    }

    /** {@code hint} projected off {@code t}, unit; a fallback axis when they are parallel. */
    static Vector3f perpendicular(Vector3f hint, Vector3f t) {
        Vector3f x = hint == null ? new Vector3f(1, 0, 0) : new Vector3f(hint);
        x.fma(-x.dot(t), t);
        if (x.lengthSquared() < 1.0E-6F) {
            x.set(Math.abs(t.x) < 0.9F ? 1 : 0, Math.abs(t.x) < 0.9F ? 0 : 1, 0);
            x.fma(-x.dot(t), t);
        }
        return x.normalize();
    }

    static Vector3f hermite(Vector3f p0, Vector3f m0, Vector3f p1, Vector3f m1, float u) {
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        return new Vector3f(p0).mul(h00).fma(h10, m0).fma(h01, p1).fma(h11, m1);
    }

    static Vector3f hermiteTangent(Vector3f p0, Vector3f m0, Vector3f p1, Vector3f m1, float u) {
        float u2 = u * u;
        float d00 = 6 * u2 - 6 * u, d10 = 3 * u2 - 4 * u + 1, d01 = -6 * u2 + 6 * u, d11 = 3 * u2 - 2 * u;
        return new Vector3f(p0).mul(d00).fma(d10, m0).fma(d01, p1).fma(d11, m1);
    }
}
