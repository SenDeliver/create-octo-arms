package dev.tentaworks.rig;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadrantClipTest {
    private static final double[] X = {1, 0, 0};
    private static final double[] Z = {0, 0, 1};

    @Test
    void identityFrameMatchesWorldPath() {
        Random rnd = new Random(1);
        for (int k = 0; k < 20000; k++) {
            int axis = rnd.nextInt(3), step = rnd.nextBoolean() ? 1 : -1;
            double[][] rect = faceRect(rnd.nextInt(7) - 3, rnd.nextInt(7) - 3, rnd.nextInt(7) - 3, axis, step);
            double[] c = {rnd.nextDouble() * 8 - 4, rnd.nextDouble() * 8 - 4, rnd.nextDouble() * 8 - 4};
            if (k % 5 == 0) c[rnd.nextInt(3)] = Math.round(c[0]);
            for (int q = 0; q < 4; q++) {
                int sx = (q & 1) == 0 ? 1 : -1, sz = (q & 2) == 0 ? 1 : -1;
                double expected = world(axis, step, rect[0], rect[1], c, sx, sz);
                double got = QuadrantClip.dist2(axis, step, rect[0], rect[1], view(c, X, Z), sx, sz);
                if (expected == Double.MAX_VALUE) assertEquals(Double.MAX_VALUE, got, "k=" + k + " q=" + q);
                else assertEquals(expected, got, 1.0E-9, "k=" + k + " q=" + q);
            }
        }
    }

    @Test
    void rotatedFrameMatchesSampling() {
        Random rnd = new Random(2);
        int compared = 0;
        for (int k = 0; k < 500; k++) {
            double[][] rot = rotation(new double[]{rnd.nextGaussian() * 0.3, 1, rnd.nextGaussian() * 0.3}, rnd.nextDouble() * Math.PI * 2);
            int axis = rnd.nextInt(3), step = rnd.nextBoolean() ? 1 : -1;
            double[][] rect = faceRect(rnd.nextInt(5) - 2, rnd.nextInt(5) - 2, rnd.nextInt(5) - 2, axis, step);
            double[] cw = {rnd.nextDouble() * 6 - 3, rnd.nextDouble() * 6 - 3, rnd.nextDouble() * 6 - 3};
            double[] cl = mulT(rot, cw);
            for (int q = 0; q < 4; q++) {
                int sx = (q & 1) == 0 ? 1 : -1, sz = (q & 2) == 0 ? 1 : -1;
                double got = QuadrantClip.dist2(axis, step, rect[0], rect[1], view(cl, mulT(rot, X), mulT(rot, Z)), sx, sz);
                double sampled = sample(rot, axis, step, rect, cw, cl, sx, sz);
                if (sampled == Double.MAX_VALUE) continue;
                compared++;
                assertTrue(got <= sampled + 1.0E-9, "k=" + k + " q=" + q);
                assertEquals(Math.sqrt(sampled), Math.sqrt(got), 0.01, "k=" + k + " q=" + q);
            }
        }
        assertTrue(compared > 200, "compared " + compared);
    }

    private static double sample(double[][] rot, int axis, int step, double[][] rect, double[] cw, double[] cl, int sx, int sz) {
        double plane = step > 0 ? rect[1][axis] : rect[0][axis];
        if ((cl[axis] - plane) * step <= 0) return Double.MAX_VALUE;
        int iu = axis == 0 ? 1 : 0, iv = axis == 2 ? 1 : 2, n = 200;
        double best = Double.MAX_VALUE;
        for (int a = 0; a <= n; a++)
            for (int b = 0; b <= n; b++) {
                double[] p = new double[3];
                p[axis] = plane;
                p[iu] = rect[0][iu] + (double) a / n;
                p[iv] = rect[0][iv] + (double) b / n;
                double[] w = mul(rot, p);
                if (sx * (w[0] - cw[0]) < -QuadrantClip.OVERLAP || sz * (w[2] - cw[2]) < -QuadrantClip.OVERLAP) continue;
                double dx = w[0] - cw[0], dy = w[1] - cw[1], dz = w[2] - cw[2];
                best = Math.min(best, dx * dx + dy * dy + dz * dz);
            }
        return best;
    }

    /** Grips' world-face path, in plain doubles. */
    private static double world(int axis, int step, double[] lo, double[] hi, double[] c, int sx, int sz) {
        double plane = step > 0 ? hi[axis] : lo[axis];
        if ((c[axis] - plane) * step <= 0) return Double.MAX_VALUE;
        double minX = lo[0], maxX = hi[0], minZ = lo[2], maxZ = hi[2];
        if (sx > 0) minX = Math.max(minX, c[0] - QuadrantClip.OVERLAP);
        else maxX = Math.min(maxX, c[0] + QuadrantClip.OVERLAP);
        if (sz > 0) minZ = Math.max(minZ, c[2] - QuadrantClip.OVERLAP);
        else maxZ = Math.min(maxZ, c[2] + QuadrantClip.OVERLAP);
        if (minX > maxX || minZ > maxZ) return Double.MAX_VALUE;
        double dx = c[0] - clamp(c[0], minX, maxX), dy = c[1] - clamp(c[1], lo[1], hi[1]), dz = c[2] - clamp(c[2], minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double[][] faceRect(int x, int y, int z, int axis, int step) {
        double[] lo = {x, y, z}, hi = {x + 1, y + 1, z + 1};
        if (step > 0) lo[axis] = hi[axis];
        else hi[axis] = lo[axis];
        return new double[][]{lo, hi};
    }

    private static double[] view(double[] c, double[] nx, double[] nz) {
        return new double[]{c[0], c[1], c[2], nx[0], nx[1], nx[2], nz[0], nz[1], nz[2]};
    }

    private static double[][] rotation(double[] axis, double angle) {
        double len = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        double x = axis[0] / len, y = axis[1] / len, z = axis[2] / len, c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        return new double[][]{
                {t * x * x + c, t * x * y - s * z, t * x * z + s * y},
                {t * x * y + s * z, t * y * y + c, t * y * z - s * x},
                {t * x * z - s * y, t * y * z + s * x, t * z * z + c}};
    }

    private static double[] mul(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    private static double[] mulT(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[1][0] * v[1] + m[2][0] * v[2], m[0][1] * v[0] + m[1][1] * v[1] + m[2][1] * v[2], m[0][2] * v[0] + m[1][2] * v[1] + m[2][2] * v[2]};
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
