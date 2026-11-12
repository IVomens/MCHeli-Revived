package com.ivomens.mcheli.bench.client;

import java.util.Arrays;

/**
 * The time between the starts of consecutive frames while recording, which is what the player sees as
 * frame time (rendering, ticking and waiting for the GPU together).
 */
final class FrameTimes {

    private static final int CAPACITY = 1 << 17;

    private final long[] intervals = new long[CAPACITY];
    private int count;
    private long last;
    private boolean recording;

    void start() {
        this.count = 0;
        this.last = 0;
        this.recording = true;
    }

    void stop() {
        this.recording = false;
    }

    void frameStarted(long nanos) {
        if (!this.recording) {
            return;
        }
        if (this.last != 0 && this.count < CAPACITY) {
            this.intervals[this.count++] = nanos - this.last;
        }
        this.last = nanos;
    }

    /** The intervals recorded since the last {@link #start()}, in nanoseconds. */
    long[] intervals() {
        return Arrays.copyOf(this.intervals, this.count);
    }

    static long[] concat(long[] first, long[] second) {
        long[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    /**
     * Average FPS over the recorded time, the "1 % low" (the average FPS of the slowest 1 % of frames)
     * and frame time percentiles in milliseconds.
     */
    record Summary(int frames, double seconds, double avgFps, double low1Fps, double p50Ms, double p95Ms,
                   double p99Ms, double maxMs) {

        static Summary of(long[] nanos) {
            if (nanos.length == 0) {
                return new Summary(0, 0, 0, 0, 0, 0, 0, 0);
            }
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            long total = Arrays.stream(sorted).sum();
            int worst = Math.max(1, sorted.length / 100);
            long worstTotal = 0;
            for (int i = sorted.length - worst; i < sorted.length; i++) {
                worstTotal += sorted[i];
            }
            double seconds = total / 1e9;
            return new Summary(sorted.length, round(seconds), round(sorted.length / seconds),
                    round(worst / (worstTotal / 1e9)), round(pct(sorted, 50)), round(pct(sorted, 95)),
                    round(pct(sorted, 99)), round(sorted[sorted.length - 1] / 1e6));
        }

        private static double pct(long[] sorted, int p) {
            int rank = (int) Math.ceil(p / 100.0 * sorted.length);
            return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))] / 1e6;
        }

        private static double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
