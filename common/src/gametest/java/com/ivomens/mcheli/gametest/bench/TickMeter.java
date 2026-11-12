package com.ivomens.mcheli.gametest.bench;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Wall time and heap allocation of each server tick, taken on the server thread from the start of the
 * tick to its end. Allocation is what {@code com.sun.management.ThreadMXBean} reports for the server
 * thread alone, so the garbage of other threads (chunk workers, the network) is not in it.
 */
final class TickMeter {

    private final com.sun.management.ThreadMXBean threads =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final long[] nanos;
    private final long[] bytes;
    private int count;
    private long startNanos;
    private long startBytes;
    private boolean started;

    TickMeter(int capacity) {
        this.nanos = new long[capacity];
        this.bytes = new long[capacity];
        if (!this.threads.isThreadAllocatedMemoryEnabled()) {
            this.threads.setThreadAllocatedMemoryEnabled(true);
        }
    }

    void tickStart() {
        this.started = true;
        this.startBytes = this.threads.getCurrentThreadAllocatedBytes();
        this.startNanos = System.nanoTime();
    }

    void tickEnd() {
        long took = System.nanoTime() - this.startNanos;
        long allocated = this.threads.getCurrentThreadAllocatedBytes() - this.startBytes;
        if (this.started && this.count < this.nanos.length) {
            this.nanos[this.count] = took;
            this.bytes[this.count] = allocated;
            this.count++;
        }
        this.started = false;
    }

    int count() {
        return this.count;
    }

    Stats time() {
        return Stats.of(Arrays.copyOf(this.nanos, this.count), 1e-6);
    }

    Stats allocation() {
        return Stats.of(Arrays.copyOf(this.bytes, this.count), 1.0);
    }

    /** Mean and percentiles of a sample, scaled (nanoseconds to milliseconds, or bytes as they are). */
    record Stats(int n, double mean, double p50, double p95, double p99, double max) {

        static Stats of(long[] values, double scale) {
            if (values.length == 0) {
                return new Stats(0, 0, 0, 0, 0, 0);
            }
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            double mean = Arrays.stream(sorted).average().orElse(0) * scale;
            return new Stats(sorted.length, mean, pct(sorted, 50) * scale, pct(sorted, 95) * scale,
                    pct(sorted, 99) * scale, sorted[sorted.length - 1] * scale);
        }

        /** Nearest-rank percentile. */
        private static double pct(long[] sorted, int p) {
            int rank = (int) Math.ceil(p / 100.0 * sorted.length);
            return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
        }
    }
}
