package dev.tentaworks.rig;

/**
 * Quadrant distance for a block face in a rotated frame (a Ship's plot space): the face rectangle is axis-aligned there,
 * the world quadrant planes through the body are not. Clips the rectangle by the two half-spaces (Sutherland–Hodgman)
 * and returns the squared distance from the body to the nearest point of what is left. Plain doubles, no game classes.
 */
final class QuadrantClip {
    /** How far a quadrant reaches past the body planes into its neighbours (blocks). */
    static final double OVERLAP = 0.5;
    private static final int MAX = 16;

    private QuadrantClip() {
    }

    /**
     * @param axis face normal axis (0 x, 1 y, 2 z), {@code step} its sign
     * @param lo   face rect min corner, {@code hi} max corner (equal on {@code axis})
     * @param view body centre (0..2), world X in this frame (3..5), world Z in this frame (6..8)
     * @param sx   +1/−1: the quadrant's side of the X plane through the body, {@code sz} of the Z plane; each side reaches
     *             {@link #OVERLAP} past the plane
     * @return squared distance, or {@link Double#MAX_VALUE} when the body is behind the face or no part of it is in the quadrant
     */
    static double dist2(int axis, int step, double[] lo, double[] hi, double[] view, int sx, int sz) {
        double plane = step > 0 ? hi[axis] : lo[axis];
        double h = view[axis] - plane;
        if (h * step <= 0) return Double.MAX_VALUE;
        int iu = axis == 0 ? 1 : 0;
        int iv = axis == 2 ? 1 : 2;
        double[] u = new double[MAX], v = new double[MAX], tu = new double[MAX], tv = new double[MAX];
        u[0] = lo[iu]; v[0] = lo[iv];
        u[1] = hi[iu]; v[1] = lo[iv];
        u[2] = hi[iu]; v[2] = hi[iv];
        u[3] = lo[iu]; v[3] = hi[iv];
        int n = 4;
        n = clip(u, v, n, tu, tv, halfSpace(axis, iu, iv, plane, view, 3, sx));
        n = clip(tu, tv, n, u, v, halfSpace(axis, iu, iv, plane, view, 6, sz));
        if (n == 0) return Double.MAX_VALUE;
        return inPlane2(u, v, n, view[iu], view[iv]) + h * h;
    }

    /** {a, b, k}: the half-space {@code a·u + b·v + k ≥ 0} of the face plane where {@code s·(p − c)·n + OVERLAP ≥ 0}. */
    private static double[] halfSpace(int axis, int iu, int iv, double plane, double[] view, int at, int s) {
        double nu = view[at + iu], nv = view[at + iv], na = view[at + axis];
        double cn = view[0] * view[at] + view[1] * view[at + 1] + view[2] * view[at + 2];
        return new double[]{s * nu, s * nv, s * (na * plane - cn) + OVERLAP};
    }

    private static int clip(double[] u, double[] v, int n, double[] ou, double[] ov, double[] hs) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double fi = hs[0] * u[i] + hs[1] * v[i] + hs[2];
            double fj = hs[0] * u[j] + hs[1] * v[j] + hs[2];
            if (fi >= 0) {
                ou[m] = u[i];
                ov[m++] = v[i];
            }
            if ((fi >= 0) != (fj >= 0)) {
                double t = fi / (fi - fj);
                ou[m] = u[i] + (u[j] - u[i]) * t;
                ov[m++] = v[i] + (v[j] - v[i]) * t;
            }
        }
        return m;
    }

    /** Squared in-plane distance from (pu, pv) to a convex polygon (possibly a segment or a point). */
    private static double inPlane2(double[] u, double[] v, int n, double pu, double pv) {
        double area = 0;
        for (int i = 0; i < n; i++) area += u[i] * v[(i + 1) % n] - u[(i + 1) % n] * v[i];
        if (Math.abs(area) > 1.0E-12) {
            boolean neg = false, pos = false;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double cross = (u[j] - u[i]) * (pv - v[i]) - (v[j] - v[i]) * (pu - u[i]);
                if (cross < -1.0E-12) neg = true;
                if (cross > 1.0E-12) pos = true;
            }
            if (!(neg && pos)) return 0;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            best = Math.min(best, segment2(u[i], v[i], u[j], v[j], pu, pv));
        }
        return best;
    }

    private static double segment2(double au, double av, double bu, double bv, double pu, double pv) {
        double du = bu - au, dv = bv - av;
        double len2 = du * du + dv * dv;
        double t = len2 < 1.0E-18 ? 0 : Math.max(0, Math.min(1, ((pu - au) * du + (pv - av) * dv) / len2));
        double eu = pu - (au + du * t), ev = pv - (av + dv * t);
        return eu * eu + ev * ev;
    }
}
