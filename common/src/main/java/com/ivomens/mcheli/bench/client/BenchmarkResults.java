package com.ivomens.mcheli.bench.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The finished (or cancelled) client benchmark, written as {@code mcheli-benchmark-<time>.json} for
 * tools and a {@code .txt} of the same name for people, both in {@code <game dir>/mcheli/benchmarks/}.
 */
record BenchmarkResults(int schema, String date, boolean quick, boolean cancelled, String cancelReason,
                        double durationSeconds, Map<String, String> system, Map<String, String> graphics,
                        Map<String, String> settings, List<StepResult> steps) {

    /** 2: the Flywheel steps run with {@code renderer = "flywheel"} (was "auto"), and steps can be aborted. */
    static final int SCHEMA = 2;
    private static final String FLYWHEEL = "flywheel";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /**
     * One measured step: what was drawn, how, and the frame times. {@code aborted} is empty, or why the
     * step was given up ("too slow (0.5 FPS)"), in which case the frames are what it measured until then.
     */
    record StepResult(int step, int vehicles, String camera, String renderer, boolean flywheelDrew,
                      boolean fixedStepAttitude, int vehiclesLoaded, FrameTimes.Summary frames, String aborted) {

        boolean wasAborted() {
            return !this.aborted.isEmpty();
        }
    }

    /** Writes both files; returns the path of the text file. */
    Path write(Path gameDir, LocalDateTime startedAt) throws IOException {
        Path dir = gameDir.resolve("mcheli").resolve("benchmarks");
        Files.createDirectories(dir);
        String name = "mcheli-benchmark-" + startedAt.format(FILE_TIME);
        Files.writeString(dir.resolve(name + ".json"), GSON.toJson(this) + "\n");
        Path text = dir.resolve(name + ".txt");
        Files.writeString(text, text());
        return text;
    }

    String text() {
        StringBuilder t = new StringBuilder();
        t.append("MCHeli: Revived client benchmark, ").append(this.date)
                .append(this.quick ? " (quick)" : "").append('\n');
        if (this.cancelled) {
            t.append("CANCELLED: ").append(this.cancelReason).append(" (the steps before it are below)\n");
        }
        t.append('\n');
        line(t, "Mod", this.graphics.get("mcheli") + " on " + this.graphics.get("loader") + ", Minecraft "
                + this.graphics.get("minecraft"));
        line(t, "GPU", this.graphics.get("glRenderer") + " (" + this.graphics.get("glVendor") + ")");
        line(t, "OpenGL", this.graphics.get("glVersion"));
        line(t, "CPU", this.system.get("cpu") + ", " + this.system.get("cpuThreads") + " threads");
        line(t, "Memory", this.system.get("ramGiB") + " GiB, Java heap " + this.system.get("javaMaxHeapGiB")
                + " GiB, " + this.system.get("java"));
        line(t, "OS", this.system.get("os"));
        line(t, "Flywheel", this.graphics.get("flywheel") + ", backend " + this.graphics.get("flywheelBackend"));
        line(t, "Shaders", "Iris " + this.graphics.get("iris") + ", Sodium " + this.graphics.get("sodium"));
        line(t, "Display", this.graphics.get("window") + ", graphics " + this.graphics.get("graphicsMode")
                + ", render distance " + this.settings.get("renderDistance") + " (yours: "
                + this.settings.get("yourRenderDistance") + ")");
        line(t, "Duration", String.format(Locale.ROOT, "%.0f s", this.durationSeconds));
        t.append('\n');
        t.append(String.format(Locale.ROOT, "%-5s %-8s %-8s %-10s %-6s %8s %8s %8s %8s %8s%n", "Veh.", "Camera",
                "Renderer", "FixedStep", "Loaded", "Avg FPS", "1% low", "p50 ms", "p95 ms", "p99 ms"));
        for (StepResult s : this.steps) {
            row(t, s);
        }
        if (this.steps.stream().anyMatch(s -> s.renderer().equals(FLYWHEEL) && !s.flywheelDrew())) {
            t.append("* Flywheel did not draw the vehicles in this step (not supported here, or a shader pack is on).\n");
        }
        t.append('\n');
        for (String comparison : comparisons()) {
            t.append(comparison).append('\n');
        }
        return t.toString();
    }

    private static void row(StringBuilder t, StepResult s) {
        t.append(String.format(Locale.ROOT, "%-5d %-8s %-8s %-10s %-6d ", s.vehicles(), s.camera(),
                s.renderer() + (s.renderer().equals(FLYWHEEL) && !s.flywheelDrew() ? "*" : ""),
                s.fixedStepAttitude() ? "on" : "off", s.vehiclesLoaded()));
        if (s.wasAborted()) {
            t.append("aborted: ").append(s.aborted()).append('\n');
            return;
        }
        t.append(String.format(Locale.ROOT, "%8.1f %8.1f %8.2f %8.2f %8.2f%n", s.frames().avgFps(),
                s.frames().low1Fps(), s.frames().p50Ms(), s.frames().p95Ms(), s.frames().p99Ms()));
    }

    /** Flywheel against the built-in renderer, and fixed-step attitude on against off, per size. */
    List<String> comparisons() {
        List<String> lines = new ArrayList<>();
        for (int vehicles : this.steps.stream().map(StepResult::vehicles).distinct().toList()) {
            Optional<StepResult> immediate = find(vehicles, "orbit", "immediate", true);
            Optional<StepResult> flywheel = find(vehicles, "orbit", FLYWHEEL, true);
            if (flywheel.isPresent() && flywheel.get().wasAborted()) {
                lines.add(String.format(Locale.ROOT, "%d vehicles: Flywheel aborted, %s", vehicles,
                        flywheel.get().aborted()));
            } else if (immediate.isPresent() && flywheel.isPresent() && !immediate.get().wasAborted()) {
                lines.add(String.format(Locale.ROOT, "%d vehicles: Flywheel %s average FPS, %s 1%% low against the"
                                + " built-in renderer", vehicles, change(flywheel.get().frames().avgFps(),
                                immediate.get().frames().avgFps()),
                        change(flywheel.get().frames().low1Fps(), immediate.get().frames().low1Fps())));
            }
            Optional<StepResult> fixedOn = find(vehicles, "cockpit", "immediate", true);
            Optional<StepResult> fixedOff = find(vehicles, "cockpit", "immediate", false);
            if (fixedOn.isPresent() && fixedOff.isPresent() && !fixedOn.get().wasAborted()
                    && !fixedOff.get().wasAborted()) {
                lines.add(String.format(Locale.ROOT, "%d vehicles: fixed-step attitude on %s average FPS against off",
                        vehicles, change(fixedOn.get().frames().avgFps(), fixedOff.get().frames().avgFps())));
            }
        }
        return lines;
    }

    private Optional<StepResult> find(int vehicles, String camera, String renderer, boolean fixedStep) {
        return this.steps.stream().filter(s -> s.vehicles() == vehicles && s.camera().equals(camera)
                && s.renderer().equals(renderer) && s.fixedStepAttitude() == fixedStep).findFirst();
    }

    private static String change(double now, double before) {
        return before <= 0 ? "n/a" : String.format(Locale.ROOT, "%+.1f%%", (now / before - 1) * 100);
    }

    private static void line(StringBuilder t, String name, String value) {
        t.append(String.format(Locale.ROOT, "%-9s %s%n", name + ":", value));
    }
}
