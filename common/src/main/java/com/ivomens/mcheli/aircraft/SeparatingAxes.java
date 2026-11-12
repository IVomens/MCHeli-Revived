package com.ivomens.mcheli.aircraft;

/** The separating axis test of two oriented boxes, on buffers the caller owns so no test allocates. */
final class SeparatingAxes {

    private static final double EPS = 1.0E-6;

    private SeparatingAxes() {
    }

    /**
     * Whether two oriented boxes overlap. {@code R} holds the dot products of box A's axes with box B's,
     * {@code a} and {@code b} the half sizes along each box's axes and {@code t} the centre offset in A's axes;
     * {@code absR} is scratch space.
     */
    static boolean overlap(double[][] R, double[][] absR, double[] a, double[] b, double[] t) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                absR[i][j] = Math.abs(R[i][j]) + EPS;
            }
        }

        double ra, rb;

        // Test A's axes
        for (int i = 0; i < 3; i++) {
            ra = a[i];
            rb = b[0] * absR[i][0] + b[1] * absR[i][1] + b[2] * absR[i][2];
            if (Math.abs(t[i]) > ra + rb) return false;
        }

        // Test B's axes
        for (int j = 0; j < 3; j++) {
            ra = a[0] * absR[0][j] + a[1] * absR[1][j] + a[2] * absR[2][j];
            rb = b[j];
            double tProj = Math.abs(t[0] * R[0][j] + t[1] * R[1][j] + t[2] * R[2][j]);
            if (tProj > ra + rb) return false;
        }

        // the nine cross products of A's and B's axes
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (separatedByCrossAxis(R, absR, a, b, t, i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the cross product of A's axis {@code i} and B's axis {@code j} separates the boxes. */
    private static boolean separatedByCrossAxis(double[][] R, double[][] absR, double[] a, double[] b, double[] t,
                                                int i, int j) {
        int i1 = (i + 1) % 3;
        int i2 = (i + 2) % 3;
        int j1 = (j + 1) % 3;
        int j2 = (j + 2) % 3;
        double ra = a[i1] * absR[i2][j] + a[i2] * absR[i1][j];
        double rb = b[j1] * absR[i][j2] + b[j2] * absR[i][j1];
        return Math.abs(t[i2] * R[i1][j] - t[i1] * R[i2][j]) > ra + rb;
    }
}
