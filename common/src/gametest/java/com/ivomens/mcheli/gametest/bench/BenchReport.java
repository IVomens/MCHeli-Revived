package com.ivomens.mcheli.gametest.bench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A finished benchmark run: the machine, the calibration time and every tier, written as JSON (for the
 * gate and for tools) and as a markdown table (for docs/BENCHMARKS.md).
 */
record BenchReport(int schema, String mode, String loader, String commit, String date, Map<String, String> machine,
                   double calibrationMs, int warmupTicks, int measureTicks, List<TierResult> tiers) {

    static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final double KIB = 1024.0;
    private static final double TIME_SLACK_MS = 0.5;
    /** Network bytes per tick and vehicle the gate allows on top of its tolerance, for nearly silent tiers. */
    private static final double NET_SLACK_BYTES = 1.0;
    /** The columns of the network table, in the order of {@link PacketMeter#group}. */
    private static final List<String> NET_GROUPS = List.of("radar contacts", "explosion effects",
            "other MCHeli messages", "move/rotation", "entity data", "teleport", "motion", "spawn/remove",
            "passengers", "other vanilla");

    static BenchReport read(Path file) throws IOException {
        return GSON.fromJson(Files.readString(file), BenchReport.class);
    }

    void writeJson(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(this) + "\n");
    }

    void writeMarkdown(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, markdown());
    }

    Optional<TierResult> tier(String key) {
        return this.tiers.stream().filter(t -> t.key().equals(key)).findFirst();
    }

    String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("Server benchmark, ").append(this.loader).append(", commit `").append(this.commit).append("`, ")
                .append(this.date).append(". ").append(this.machine.get("cpu")).append(", ")
                .append(this.machine.get("cpuThreads")).append(" threads, ").append(this.machine.get("java"))
                .append(". Calibration loop ").append(fmt(this.calibrationMs)).append(" ms. ")
                .append(this.warmupTicks).append(" warm-up ticks, ").append(this.measureTicks)
                .append(" measured ticks per tier.\n\n");
        md.append("| Workload | Vehicles | Crew / gunners / wingmen | Entities | ms mean | p50 | p95 | p99 "
                + "| alloc KiB/tick | net B/tick/vehicle | tracked |\n");
        md.append("|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        List<TierResult> sorted = new ArrayList<>(this.tiers);
        sorted.sort(Comparator.comparing(TierResult::workload, Comparator.comparingInt(w -> Workload.byId(w).ordinal()))
                .thenComparingInt(TierResult::vehicles));
        for (TierResult t : sorted) {
            md.append("| ").append(t.workload()).append(" | ").append(t.vehicles()).append(" | ")
                    .append(t.crewAboard()).append(" / ").append(t.gunners()).append(" / ").append(t.wingmen())
                    .append(" | ").append(t.entities()).append(" | ").append(fmt(t.msMean())).append(" | ")
                    .append(fmt(t.msP50())).append(" | ").append(fmt(t.msP95())).append(" | ")
                    .append(fmt(t.msP99())).append(" | ").append(fmt(t.allocMean() / KIB)).append(" | ")
                    .append(fmt(t.netBytesPerVehicle())).append(" | ").append(fmt(t.trackedVehicles()))
                    .append(" |\n");
        }
        md.append("\nNetwork, bytes (packets) per tick the observer was sent about MCHeli, by kind; the pilot column is "
                + "everything one crew member was sent about MCHeli.\n\n| Tier |");
        NET_GROUPS.forEach(group -> md.append(' ').append(group).append(" |"));
        md.append(" total | B/tick/vehicle | pilot B/tick |\n|---|");
        md.append("---:|".repeat(NET_GROUPS.size() + 3)).append('\n');
        for (TierResult t : sorted) {
            if (t.netByGroup() == null) {
                continue;
            }
            md.append("| ").append(t.key()).append(" |");
            for (String group : NET_GROUPS) {
                double bytes = t.netByGroup().getOrDefault(group, 0.0);
                double count = t.netCountByGroup().getOrDefault(group, 0.0);
                md.append(' ').append(bytes == 0 ? "-" : fmt(bytes) + " (" + fmt(count) + ")").append(" |");
            }
            md.append(' ').append(fmt(t.netModBytes() + t.netVanillaBytes())).append(" | ")
                    .append(fmt(t.netBytesPerVehicle())).append(" | ").append(fmt(t.pilotBytes())).append(" |\n");
        }
        appendProjectiles(md, sorted);
        return md.toString();
    }

    /** Projectiles alive, fired per tick and their lifetime, for the tiers that had any. */
    private static void appendProjectiles(StringBuilder md, List<TierResult> tiers) {
        List<TierResult> firing = tiers.stream()
                .filter(t -> t.projectiles() != null && t.projectiles().spawnedPerTick() > 0).toList();
        if (firing.isEmpty()) {
            return;
        }
        md.append("\nProjectiles while measured: alive after a tick (mean), of those frozen in chunks that tick no "
                + "entities, fired or split off per tick, and ticks flown by those that ended in the window.\n\n"
                + "| Tier | alive | frozen | spawned/tick | lifetime ticks |\n|---|---:|---:|---:|---:|\n");
        for (TierResult t : firing) {
            md.append("| ").append(t.key()).append(" | ").append(fmt(t.projectiles().alive())).append(" | ")
                    .append(fmt(t.projectiles().frozen())).append(" | ")
                    .append(fmt(t.projectiles().spawnedPerTick())).append(" | ")
                    .append(fmt(t.projectiles().lifetimeTicks())).append(" |\n");
        }
    }

    /**
     * What regressed against the baseline: allocation per tick beyond {@code allocTolerance} (plus a
     * small absolute allowance for nearly idle tiers), or the median tick time, divided by each run's
     * calibration time, beyond {@code timeTolerance}. Empty when the run passes.
     */
    List<String> regressionsAgainst(BenchReport baseline, double allocTolerance, double timeTolerance,
                                    double netTolerance) {
        List<String> problems = new ArrayList<>();
        for (TierResult now : this.tiers) {
            Optional<TierResult> before = baseline.tier(now.key());
            if (before.isEmpty()) {
                problems.add(now.key() + ": not in the baseline; record it with -Pbench=record");
                continue;
            }
            problems.addAll(compare(now, before.get(), baseline, allocTolerance, timeTolerance));
            problems.addAll(compareNetwork(now, before.get(), netTolerance));
        }
        return problems;
    }

    /** One line per gated number of a tier, for the log. */
    List<String> comparisonLines(BenchReport baseline) {
        List<String> lines = new ArrayList<>();
        for (TierResult now : this.tiers) {
            baseline.tier(now.key()).ifPresent(before -> lines.add(String.format(Locale.ROOT,
                    "%s: alloc %.1f KiB/tick (baseline %.1f, %+.1f%%), p50 %.2f ms = %.3f calibrations "
                            + "(baseline %.3f, %+.1f%%), net %.2f B/tick/vehicle (baseline %.2f, %+.1f%%)",
                    now.key(), now.allocMean() / KIB, before.allocMean() / KIB,
                    change(now.allocMean(), before.allocMean()), now.msP50(), now.msP50() / this.calibrationMs,
                    before.msP50() / baseline.calibrationMs(),
                    change(now.msP50() / this.calibrationMs, before.msP50() / baseline.calibrationMs()),
                    now.netBytesPerVehicle(), before.netBytesPerVehicle(),
                    change(now.netBytesPerVehicle(), before.netBytesPerVehicle()))));
        }
        return lines;
    }

    private List<String> compare(TierResult now, TierResult before, BenchReport baseline, double allocTolerance,
                                 double timeTolerance) {
        List<String> problems = new ArrayList<>();
        // 64 KiB a tick of slack keeps a tier that allocates almost nothing from failing on noise
        double allocLimit = before.allocMean() * (1 + allocTolerance) + 64 * KIB;
        if (now.allocMean() > allocLimit) {
            problems.add(String.format(Locale.ROOT, "%s: allocates %.1f KiB/tick, baseline %.1f (limit %.1f, +%.0f%%)",
                    now.key(), now.allocMean() / KIB, before.allocMean() / KIB, allocLimit / KIB,
                    allocTolerance * 100));
        }
        double relativeNow = now.msP50() / this.calibrationMs;
        double relativeBefore = before.msP50() / baseline.calibrationMs();
        // half a millisecond of slack keeps nearly idle tiers from failing on scheduler noise
        double timeLimit = relativeBefore * (1 + timeTolerance) + TIME_SLACK_MS / this.calibrationMs;
        if (relativeNow > timeLimit) {
            problems.add(String.format(Locale.ROOT,
                    "%s: median tick %.2f ms is %.3f calibration loops, baseline %.3f (limit +%.0f%%)", now.key(),
                    now.msP50(), relativeNow, relativeBefore, timeTolerance * 100));
        }
        return problems;
    }

    /**
     * The network bytes per tick and tracked vehicle may rise by {@code netTolerance} (plus
     * {@link #NET_SLACK_BYTES}). Unlike time they do not depend on the machine.
     */
    private static List<String> compareNetwork(TierResult now, TierResult before, double netTolerance) {
        double limit = before.netBytesPerVehicle() * (1 + netTolerance) + NET_SLACK_BYTES;
        if (now.netBytesPerVehicle() <= limit) {
            return List.of();
        }
        return List.of(String.format(Locale.ROOT, "%s: sends %.2f B/tick/vehicle, baseline %.2f (limit %.2f, +%.0f%%)",
                now.key(), now.netBytesPerVehicle(), before.netBytesPerVehicle(), limit, netTolerance * 100));
    }

    private static double change(double now, double before) {
        return before == 0 ? 0 : (now / before - 1) * 100;
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, value >= 100 ? "%.0f" : "%.2f", value);
    }
}
