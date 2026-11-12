package com.ivomens.mcheli.gametest.bench;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A fixed piece of work timed on the server thread, to tell a slow machine from slow code. It mixes
 * what a server tick does: floating point vector math with trigonometry, hash map churn with boxed
 * keys, and sorting; it is deterministic and takes a few milliseconds. The gate compares tick times
 * divided by this time, so a CI runner that is uniformly slower than the machine that recorded the
 * baseline does not fail it.
 */
final class Calibration {

    private static final int ROUNDS = 15;
    private static final int VECTORS = 60_000;
    private static final int MAP_KEYS = 40_000;
    private static final int SORTED = 80_000;

    private Calibration() {
    }

    /**
     * Milliseconds of the fastest round, after warming the code up. The fastest, not the median: other
     * work on the machine only ever makes a round slower, so the minimum is the steadiest measure of
     * what the machine can do.
     */
    static double measure() {
        double[] times = new double[ROUNDS];
        long sink = 0;
        for (int warm = 0; warm < 5; warm++) {
            sink += round(warm);
        }
        for (int i = 0; i < ROUNDS; i++) {
            long start = System.nanoTime();
            sink += round(i);
            times[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(times);
        if (sink == 42) {
            // keeps the work from being optimised away
            System.out.print("");
        }
        return times[0];
    }

    private static long round(int salt) {
        long seed = 0x9E3779B97F4A7C15L ^ salt;
        double x = 0;
        double y = 0;
        double z = 0;
        for (int i = 0; i < VECTORS; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double a = (seed >>> 11) * 0x1.0p-53 * Math.PI * 2;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double nx = x * c - z * s + 0.1;
            double nz = x * s + z * c;
            double len = Math.sqrt(nx * nx + y * y + nz * nz) + 1e-9;
            x = nx / len;
            y = (y + Math.atan2(nz, nx)) * 0.5;
            z = nz / len;
        }
        Map<Integer, double[]> map = new HashMap<>();
        for (int i = 0; i < MAP_KEYS; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int key = (int) (seed >>> 40) % (MAP_KEYS / 2);
            map.merge(key, new double[]{x, y, z}, (old, add) -> {
                old[0] += add[0];
                return old;
            });
        }
        long[] values = new long[SORTED];
        for (int i = 0; i < SORTED; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            values[i] = seed;
        }
        Arrays.sort(values);
        return map.size() + values[SORTED / 2] + Double.doubleToLongBits(x + y + z);
    }
}
