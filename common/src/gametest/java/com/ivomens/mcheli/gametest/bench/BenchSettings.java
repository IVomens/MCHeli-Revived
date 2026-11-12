package com.ivomens.mcheli.gametest.bench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a benchmark run measures, from system properties the run configurations set
 * ({@code ./gradlew runBenchmarks}, {@code runBenchmarkGate}, see neoforge/build.gradle):
 * <ul>
 *   <li>{@code mcheli.bench}: {@code full} (every workload at 1 to 500 vehicles), {@code gate} (the CI
 *   subset, compared with the committed baseline) or {@code record} (the CI subset, written as the new
 *   baseline); empty for no benchmark;</li>
 *   <li>{@code mcheli.bench.workloads}, {@code mcheli.bench.vehicles}: comma lists that narrow a full run;</li>
 *   <li>{@code mcheli.bench.warmup}, {@code mcheli.bench.measure}: ticks per tier;</li>
 *   <li>{@code mcheli.bench.out}: where the results go; {@code mcheli.bench.baseline}: the baseline file;</li>
 *   <li>{@code mcheli.bench.allocTolerance}, {@code mcheli.bench.timeTolerance},
 *   {@code mcheli.bench.netTolerance}: the gate's limits.</li>
 * </ul>
 */
record BenchSettings(String mode, List<Tier> tiers, int warmupTicks, int measureTicks, Path outDir, Path baseline,
                     double allocTolerance, double timeTolerance, double netTolerance) {

    static final List<Integer> FULL_COUNTS = List.of(1, 25, 50, 100, 200, 500);
    static final int GATE_COUNT = 100;
    /** Enough for the JIT to compile the hot paths: the game test server runs ticks back to back. */
    static final int WARMUP_TICKS = 600;
    static final int MEASURE_TICKS = 600;

    record Tier(Workload workload, int vehicles) {
        String key() {
            return this.workload.id() + "/" + this.vehicles;
        }
    }

    static BenchSettings fromSystemProperties() {
        String mode = System.getProperty("mcheli.bench", "").trim();
        boolean full = mode.equals("full");
        List<Tier> tiers = new ArrayList<>();
        if (full) {
            List<Workload> workloads = Arrays.stream(System.getProperty("mcheli.bench.workloads",
                    "parked,flying,firefight,mixed").split(",")).map(Workload::byId).toList();
            List<Integer> counts = Arrays.stream(System.getProperty("mcheli.bench.vehicles", "")
                    .split(",")).filter(s -> !s.isBlank()).map(s -> Integer.parseInt(s.trim())).toList();
            for (Workload workload : workloads) {
                for (int count : counts.isEmpty() ? FULL_COUNTS : counts) {
                    tiers.add(new Tier(workload, count));
                }
            }
        } else {
            for (Workload workload : List.of(Workload.PARKED, Workload.FLYING, Workload.FIREFIGHT)) {
                tiers.add(new Tier(workload, GATE_COUNT));
            }
        }
        return new BenchSettings(mode, tiers, Integer.getInteger("mcheli.bench.warmup", WARMUP_TICKS),
                Integer.getInteger("mcheli.bench.measure", MEASURE_TICKS),
                Path.of(System.getProperty("mcheli.bench.out", "benchmarks")),
                Path.of(System.getProperty("mcheli.bench.baseline", "bench-baseline.json")),
                Double.parseDouble(System.getProperty("mcheli.bench.allocTolerance", "0.10")),
                Double.parseDouble(System.getProperty("mcheli.bench.timeTolerance", "0.35")),
                Double.parseDouble(System.getProperty("mcheli.bench.netTolerance", "0.10")));
    }

    boolean gate() {
        return this.mode.equals("gate");
    }

    boolean record() {
        return this.mode.equals("record");
    }
}
