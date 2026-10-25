package dev.tentaworks.client;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Pure geometry of the drawn Tentacles: Step arcs, pad normals, the elbow pole, lights along a segment, the first-person cut. */
final class TentacleMath {
    /** Lift of a Step arc above its chord, blocks: a short hop for a short Step, at most one block. */
    static final double LIFT_MIN = 0.3, LIFT_PER_BLOCK = 0.3, LIFT_MAX = 1.0;
    /** How far the arc's control point pulls toward the body: the arc stays between the chord and the body, where nothing stands. */
    static final double PULL = 0.5;
    /** Faces turned more than this against each other (cos) lift toward the body instead of along the blended normal. */
    static final double OPPOSED = -0.3;
    /** The arc's control point keeps this far from the body along its pull and its toward-body lift, so the arc never crosses the wearer. */
    static final double BODY_CLEAR = 0.9;
    /** Least toward-body lift worth taking between opposed faces; with less room the arc swings sideways instead. */
    static final double MIN_ROOM = 0.3;

    private TentacleMath() {
    }

    /**
     * Tip of a Step at eased progress {@code e}: a quadratic Bezier from {@code a} to {@code b} whose control point is pulled
     * from the chord toward {@code body} (never nearer than {@link #BODY_CLEAR}) and lifted along the Step's lift axis: the
     * blended face normal, the target's alone when {@code nA} is null (a first Step from the free pose), or, between opposed
     * faces, toward the body while there is room and sideways when there is not.
     */
    static Vec3 arc(Vec3 a, Vec3 b, Vec3 body, Vec3 nA, Vec3 nB, double e) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toBody = body.subtract(mid);
        double dist = toBody.length();
        double budget = Math.max(0, dist - BODY_CLEAR);
        Vec3 pull = dist < 1.0E-6 ? Vec3.ZERO : toBody.scale(Math.min(PULL, budget / dist));
        Vec3 q = liftAxis(nA, nB, mid, body);
        double h = 2 * Math.min(LIFT_MAX, LIFT_MIN + LIFT_PER_BLOCK * a.distanceTo(b));
        Vec3 lift;
        if (nA == null) lift = nB.scale(h);
        else if (nA.dot(nB) < OPPOSED) {
            double room = budget - pull.length();
            if (room >= MIN_ROOM) lift = q.scale(Math.min(h, room));
            else {
                Vec3 side = b.subtract(a).cross(q);
                if (side.lengthSqr() < 1.0E-6) side = liftAxis(null, q, mid, mid);
                lift = side.normalize().scale(0.5 * h);
            }
        } else lift = normalBetween(nA, nB, q, 0.5).scale(h);
        Vec3 c = mid.add(pull).add(lift);
        double u = 1 - e;
        return a.scale(u * u).add(c.scale(2 * e * u)).add(b.scale(e * e));
    }

    /** The pad normal part way through a Step: between the two face normals, swinging through {@code q} when they oppose. */
    static Vec3 normalBetween(Vec3 nA, Vec3 nB, Vec3 q, double e) {
        double opposed = Math.max(0, -nA.dot(nB));
        Vec3 n = nA.scale(1 - e).add(nB.scale(e)).add(q.scale(Math.sin(Math.PI * e) * opposed));
        return n.lengthSqr() < 1.0E-6 ? nB : n.normalize();
    }

    /** Where a Step between opposed faces lifts: from the chord's middle toward the body, or any direction across {@code nB} when the body sits on the chord. */
    static Vec3 liftAxis(Vec3 nA, Vec3 nB, Vec3 mid, Vec3 body) {
        Vec3 q = body.subtract(mid);
        if (q.lengthSqr() > 0.09) return q.normalize();
        Vec3 n = nA == null ? nB : nA;
        Vec3 p = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return p.subtract(n.scale(n.dot(p))).normalize();
    }

    /**
     * Elbow pole: {@code hint} made perpendicular to the arm direction {@code u}. As the arm lines up with the hint the pole
     * fades over to {@code alt1} (or {@code alt2} when that one lines up too), so it never spins or snaps. Unit length.
     */
    static Vector3f pole(Vector3f u, Vector3f hint, Vector3f alt1, Vector3f alt2) {
        Vector3f p = new Vector3f(hint).fma(-hint.dot(u), u);
        float len = p.length();
        float fade = 0.35F;
        if (len < fade) {
            Vector3f a = new Vector3f(alt1).fma(-alt1.dot(u), u);
            float w = Math.min(1, a.length() / 0.5F);
            a.mul(w).fma(1 - w, new Vector3f(alt2).fma(-alt2.dot(u), u));
            if (a.lengthSquared() > 1.0E-6F) p.fma((fade - len) / a.length(), a);
        }
        if (p.lengthSquared() < 1.0E-8F) p.set(0, 0, 1).fma(-u.z, u);
        return p.normalize();
    }

    /**
     * The pad's side axis: a world axis fixed to the gripped face (up on a wall, east on a floor or ceiling, fading between
     * the two on a tilted face), so a planted pad does not turn with the body. Null when none fits.
     */
    static Vector3f padSide(Vector3f n, Vector3f worldUp, Vector3f worldX) {
        float w = Mth.clamp((Math.abs(n.dot(worldUp)) - 0.6F) / 0.35F, 0, 1);
        Vector3f side = new Vector3f(worldUp).fma(-worldUp.dot(n), n).mul(1 - w);
        side.fma(w, new Vector3f(worldX).fma(-worldX.dot(n), n));
        return side.lengthSquared() < 1.0E-4F ? null : side.normalize();
    }

    /** Packed light between two samples, block and sky channels apart. */
    static int lerpLight(int l0, int l1, float t) {
        if (l0 == l1) return l0;
        int block = Math.round(Mth.lerp(t, LightTexture.block(l0), LightTexture.block(l1)));
        int sky = Math.round(Mth.lerp(t, LightTexture.sky(l0), LightTexture.sky(l1)));
        return LightTexture.pack(block, sky);
    }

    /**
     * Which part of the segment a→b (parameter 0..1) lies outside a sphere of {@code radius} at the origin: {lo, hi}, the whole
     * segment when it misses, null when the sphere swallows it or cuts its middle (then nothing is drawn, as before).
     */
    static float[] outsideSphere(Vector3f a, Vector3f b, float radius) {
        Vector3f ab = new Vector3f(b).sub(a);
        float qa = ab.lengthSquared();
        if (qa < 1.0E-10F) return a.length() < radius ? null : new float[]{0, 1};
        float qb = 2 * a.dot(ab), qc = a.lengthSquared() - radius * radius;
        float disc = qb * qb - 4 * qa * qc;
        if (disc <= 0) return new float[]{0, 1};
        float sq = (float) Math.sqrt(disc);
        float t0 = (-qb - sq) / (2 * qa), t1 = (-qb + sq) / (2 * qa);
        if (t1 <= 0 || t0 >= 1) return new float[]{0, 1};
        if (t0 <= 0 && t1 >= 1) return null;
        if (t0 <= 0) return new float[]{t1, 1};
        if (t1 >= 1) return new float[]{0, t0};
        return null;
    }

    /** Exact critically damped spring step toward {@code goal}: {@code x} and {@code v} updated in place, {@code dt} in ticks. */
    static void spring(Vector3f x, Vector3f v, Vector3f goal, float halflifeTicks, float dt) {
        float y = 2 * 0.6931472F / halflifeTicks;
        Vector3f j0 = new Vector3f(x).sub(goal);
        Vector3f j1 = new Vector3f(v).fma(y, j0);
        float e = (float) Math.exp(-y * dt);
        x.set(j0).fma(dt, j1).mul(e).add(goal);
        v.set(v).fma(-y * dt, j1).mul(e);
    }
}
