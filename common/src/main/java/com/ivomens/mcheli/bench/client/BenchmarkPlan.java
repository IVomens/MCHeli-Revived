package com.ivomens.mcheli.bench.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The steps of the client benchmark, in order. For each scene size the camera first circles the scene
 * with the built-in renderer, then (with Flywheel installed) with {@code VehicleRenderer = flywheel},
 * which uses Flywheel on whatever backend it runs, so the step measures what {@code auto} avoids; then
 * the player sits in a parked helicopter looking at the rows with {@code FixedStepAttitude} on and off,
 * which is the only place that setting does any work. Every step lets the scene settle, then measures
 * for a fixed number of client ticks along a fixed camera path.
 * <p>
 * The cockpit is measured in the order on, off, off, on, each for half the time, and the two halves
 * of a setting count as one result. Measured in one order only, whatever the first cockpit step
 * pays for (getting seated, the HUD's first frames, a machine warming up or throttling) lands on one
 * setting and reads as a difference between the two.
 */
record BenchmarkPlan(List<Step> steps, int settleTicks, int measureTicks, boolean quick) {

    private static final List<Integer> SIZES = List.of(0, 25, 50, 100, 150);
    private static final List<Integer> QUICK_SIZES = List.of(0, 25);

    enum Camera {
        ORBIT, COCKPIT;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    record Step(int vehicles, Camera camera, boolean flywheel, boolean fixedStep) {

        String describe() {
            return this.vehicles + " vehicles, " + (this.camera == Camera.ORBIT ? "orbiting camera" : "cockpit")
                    + ", " + (this.flywheel ? "Flywheel" : "immediate renderer")
                    + (this.camera == Camera.COCKPIT ? ", fixed-step attitude " + (this.fixedStep ? "on" : "off") : "");
        }

        /** The {@code VehicleRenderer} value the step runs with. */
        String renderer() {
            return this.flywheel ? "flywheel" : "immediate";
        }
    }

    static BenchmarkPlan create(boolean quick, boolean flywheelInstalled) {
        List<Step> steps = new ArrayList<>();
        for (int vehicles : quick ? QUICK_SIZES : SIZES) {
            steps.add(new Step(vehicles, Camera.ORBIT, false, true));
            if (flywheelInstalled) {
                steps.add(new Step(vehicles, Camera.ORBIT, true, true));
            }
            for (boolean fixedStep : new boolean[]{true, false, false, true}) {
                steps.add(new Step(vehicles, Camera.COCKPIT, false, fixedStep));
            }
        }
        return quick ? new BenchmarkPlan(steps, 20, 80, true) : new BenchmarkPlan(steps, 60, 400, false);
    }

    /** Client ticks a step measures: the cockpit steps measure half, as each setting runs twice. */
    int measureTicks(Step step) {
        return step.camera() == Camera.COCKPIT ? this.measureTicks / 2 : this.measureTicks;
    }

    /** Rough length in seconds, for the start message: steps plus time to spawn each size. */
    int estimatedSeconds() {
        long sizes = this.steps.stream().map(Step::vehicles).distinct().count();
        int ticks = this.steps.stream().mapToInt(step -> this.settleTicks + measureTicks(step)).sum();
        return (int) (ticks / 20 + sizes * 8);
    }
}
