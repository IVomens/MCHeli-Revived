package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.bench.BenchmarkLayout;
import com.ivomens.mcheli.bench.BenchmarkScene;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One run of the client benchmark, ticked on the client thread. The scene lives on the integrated
 * server ({@link BenchmarkScene}); this side asks the server thread for every change with
 * {@code server.execute} and only ever looks at what reached the client. Steps follow
 * {@link BenchmarkPlan}: spawn a size, then per step settle and measure.
 */
final class BenchmarkRun {

    /** How long a size may take to show up on the client before the steps start anyway. */
    private static final int MAX_SPAWN_TICKS = 400;
    private static final int MIN_SPAWN_TICKS = 40;
    private static final int MAX_SEATING_TICKS = 200;

    enum Phase { PREPARING, SPAWNING, SETTLING, MEASURING }

    private final Minecraft mc;
    private final IntegratedServer server;
    private final UUID playerId;
    private final BenchmarkPlan plan;
    private final BenchmarkLayout start;
    private final AtomicReference<BenchmarkScene> scene = new AtomicReference<>();
    private final FrameTimes frames = new FrameTimes();
    private final StepWatchdog watchdog = new StepWatchdog();
    private final BenchmarkCamera camera = new BenchmarkCamera();
    private final List<BenchmarkResults.StepResult> results = new ArrayList<>();
    /** The frame times of every step measured so far, by step, to pool a setting measured twice. */
    private final Map<BenchmarkPlan.Step, long[]> measuredFrames = new HashMap<>();
    private Phase phase = Phase.PREPARING;
    private int phaseTicks;
    private int step;
    private int seatWait;

    BenchmarkRun(Minecraft mc, IntegratedServer server, BenchmarkPlan plan) {
        this.mc = mc;
        this.server = server;
        this.plan = plan;
        LocalPlayer player = mc.player;
        this.playerId = player.getUUID();
        int x = (int) Math.floor(player.getX());
        int z = (int) Math.floor(player.getZ());
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        this.start = new BenchmarkLayout(new Vec3(x + 0.5, ground, z + 0.5), 0);
        Vec3 origin = this.start.start();
        onServer(p -> this.scene.set(BenchmarkScene.begin(p, origin)));
    }

    Phase phase() {
        return this.phase;
    }

    List<BenchmarkResults.StepResult> results() {
        return this.results;
    }

    BenchmarkPlan.Step currentStep() {
        return this.plan.steps().get(Math.min(this.step, this.plan.steps().size() - 1));
    }

    int stepNumber() {
        return Math.min(this.step + 1, stepCount());
    }

    int stepCount() {
        return this.plan.steps().size();
    }

    boolean quick() {
        return this.plan.quick();
    }

    /** Ticks left in the current phase, for the overlay. */
    int ticksLeft() {
        return switch (this.phase) {
            case SETTLING -> this.plan.settleTicks() - this.phaseTicks + this.plan.measureTicks(currentStep());
            case MEASURING -> this.plan.measureTicks(currentStep()) - this.phaseTicks;
            default -> 0;
        };
    }

    /** 0 to 1 over the whole run. */
    double progress() {
        double perStep = this.plan.settleTicks() + this.plan.measureTicks(currentStep());
        double within = switch (this.phase) {
            case SETTLING -> this.phaseTicks;
            case MEASURING -> this.plan.settleTicks() + this.phaseTicks;
            default -> 0;
        };
        return (this.step + within / perStep) / this.plan.steps().size();
    }

    void frameStarted(long nanos) {
        this.frames.frameStarted(nanos);
        this.watchdog.frameStarted(nanos);
    }

    /** One client tick. @return true once every step is done */
    boolean tick() {
        onServerScene(BenchmarkScene::tick);
        this.phaseTicks++;
        return switch (this.phase) {
            case PREPARING -> {
                if (this.scene.get() != null) {
                    beginSize();
                }
                yield false;
            }
            case SPAWNING -> {
                if (this.phaseTicks >= MIN_SPAWN_TICKS && (loadedVehicles() >= expectedVehicles()
                        || this.phaseTicks > MAX_SPAWN_TICKS)) {
                    beginStep();
                }
                yield false;
            }
            case SETTLING, MEASURING -> {
                String tooSlow = this.watchdog.verdict();
                if (tooSlow != null) {
                    yield abortStep(tooSlow);
                }
                if (this.phase == Phase.SETTLING) {
                    settle();
                    yield false;
                }
                yield measure();
            }
        };
    }

    private void beginSize() {
        int count = currentStep().vehicles();
        onServerScene(scene -> scene.populate(count));
        this.phase = Phase.SPAWNING;
        this.phaseTicks = 0;
    }

    private void beginStep() {
        BenchmarkPlan.Step s = currentStep();
        ClientSettings.applyStep(s);
        BenchmarkLayout layout = layout();
        if (s.camera() == BenchmarkPlan.Camera.ORBIT) {
            float[] angles = BenchmarkCamera.startAngles(layout);
            Vec3 at = layout.orbitPosition(0);
            onServer(p -> this.scene.get().moveCamera(p, at, angles[0], angles[1]));
        } else {
            onServer(p -> this.scene.get().seatPilot(p));
        }
        this.phase = Phase.SETTLING;
        this.phaseTicks = 0;
        this.seatWait = 0;
        this.watchdog.start();
    }

    private void settle() {
        BenchmarkPlan.Step s = currentStep();
        LocalPlayer player = this.mc.player;
        if (s.camera() == BenchmarkPlan.Camera.ORBIT) {
            if (player.getVehicle() == null) {
                this.camera.place(player, layout(), 0);
            }
        } else if (!(player.getVehicle() instanceof AircraftEntity) && ++this.seatWait < MAX_SEATING_TICKS) {
            // settling starts once the seat reached the client; ask again now and then
            this.phaseTicks = 0;
            if (this.seatWait % 20 == 0) {
                onServer(p -> this.scene.get().seatPilot(p));
            }
            return;
        }
        if (this.phaseTicks >= this.plan.settleTicks()) {
            this.frames.start();
            this.phase = Phase.MEASURING;
            this.phaseTicks = 0;
        }
    }

    private boolean measure() {
        BenchmarkPlan.Step s = currentStep();
        if (s.camera() == BenchmarkPlan.Camera.ORBIT && this.mc.player.getVehicle() == null) {
            this.camera.place(this.mc.player, layout(), this.phaseTicks / (double) this.plan.measureTicks(s));
        }
        if (this.phaseTicks < this.plan.measureTicks(s)) {
            return false;
        }
        return endStep("", this.frames.intervals());
    }

    /**
     * The machine cannot draw this step at a usable rate: records why, with the frames it measured (if
     * any), and goes on with the next step.
     */
    private boolean abortStep(String reason) {
        ModLogger.warn("Client benchmark: step {} ({}) aborted: {}", this.step + 1, currentStep().describe(), reason);
        // a step stopped while settling measured nothing yet
        long[] measured = this.phase == Phase.MEASURING ? this.frames.intervals() : new long[0];
        return endStep(reason, measured);
    }

    /** Records the step and starts the next; true when it was the last. */
    private boolean endStep(String aborted, long[] measured) {
        BenchmarkPlan.Step s = currentStep();
        this.frames.stop();
        this.watchdog.stop();
        record(s, measured, aborted);
        this.step++;
        if (this.step >= this.plan.steps().size()) {
            return true;
        }
        if (currentStep().vehicles() != s.vehicles()) {
            beginSize();
        } else {
            beginStep();
        }
        return false;
    }

    /**
     * Keeps a measured step. A setting measured twice (the cockpit steps) is one result over the
     * frames of both halves, numbered as its first half.
     */
    private void record(BenchmarkPlan.Step s, long[] measured, String aborted) {
        ModLogger.info("Client benchmark step {}: {}, {}", this.step + 1, s.describe(), FrameTimes.Summary.of(measured));
        BenchmarkResults.StepResult result = new BenchmarkResults.StepResult(this.step + 1, s.vehicles(),
                s.camera().id(), s.renderer(), flywheelDrew(), s.fixedStep(), loadedVehicles(),
                FrameTimes.Summary.of(measured), aborted);
        long[] earlier = this.measuredFrames.get(s);
        if (earlier == null) {
            this.measuredFrames.put(s, measured);
            this.results.add(result);
            return;
        }
        long[] both = FrameTimes.concat(earlier, measured);
        this.measuredFrames.put(s, both);
        for (int i = 0; i < this.results.size(); i++) {
            BenchmarkResults.StepResult first = this.results.get(i);
            if (first.vehicles() == s.vehicles() && first.camera().equals(result.camera())
                    && first.renderer().equals(result.renderer()) && first.fixedStepAttitude() == s.fixedStep()) {
                this.results.set(i, new BenchmarkResults.StepResult(first.step(), first.vehicles(), first.camera(),
                        first.renderer(), first.flywheelDrew() || result.flywheelDrew(), first.fixedStepAttitude(),
                        Math.max(first.vehiclesLoaded(), result.vehiclesLoaded()), FrameTimes.Summary.of(both),
                        first.aborted().isEmpty() ? aborted : first.aborted()));
            }
        }
    }

    /** Ends the run on the server: removes the scene and puts the world and the player back. */
    void endOnServer() {
        onServer(p -> {
            BenchmarkScene current = this.scene.get();
            if (current != null) {
                current.end(p);
            }
        });
    }

    /** The player left the world: only the vehicles are removed, if the server still runs. */
    void abandonOnServer() {
        onServerScene(BenchmarkScene::abandon);
    }

    Component status() {
        return Component.literal(switch (this.phase) {
            case PREPARING -> "preparing the scene";
            case SPAWNING -> "spawning " + currentStep().vehicles() + " vehicles (" + loadedVehicles() + " here)";
            case SETTLING -> "settling";
            case MEASURING -> "measuring";
        });
    }

    private BenchmarkLayout layout() {
        return new BenchmarkLayout(this.start.start(), currentStep().vehicles());
    }

    /** The scene's vehicles plus the camera helicopter. */
    private int expectedVehicles() {
        return currentStep().vehicles() + 1;
    }

    private int loadedVehicles() {
        int n = 0;
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity) {
                n++;
            }
        }
        return n;
    }

    private boolean flywheelDrew() {
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity vehicle && VehicleRenderBackend.isDrawnByFlywheel(vehicle)) {
                return true;
            }
        }
        return false;
    }

    private void onServer(Consumer<ServerPlayer> action) {
        this.server.execute(() -> {
            ServerPlayer player = this.server.getPlayerList().getPlayer(this.playerId);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    private void onServerScene(Consumer<BenchmarkScene> action) {
        this.server.execute(() -> {
            BenchmarkScene current = this.scene.get();
            if (current != null) {
                action.accept(current);
            }
        });
    }
}
