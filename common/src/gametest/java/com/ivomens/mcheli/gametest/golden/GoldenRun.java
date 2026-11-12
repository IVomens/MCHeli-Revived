package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives one golden case through the level tick, one or more times in a row.
 * <p>
 * {@link GoldenTickHook} calls {@link #beforeLevelTick} before the overworld ticks its entities,
 * which is where a real server has already handled the packets that arrived since the last tick,
 * and {@link #afterLevelTick} once they have ticked, which is when a row is recorded.
 */
final class GoldenRun {

    private final GoldenCase golden;
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final TestTerrain terrain;
    private final int repetitions;
    private final List<TrajectoryColumns.Column> columns;
    private final List<GoldenFile> recordings = new ArrayList<>();

    private PinnedSettings settings;
    private AircraftEntity vehicle;
    private Pilot pilot;
    @Nullable
    private Script.Assist assist;
    private List<String> rows;
    /** Tick of the current repetition, or -1 while waiting for the course to load. */
    private int tick = -1;
    private String failure;
    private boolean finished;

    GoldenRun(GoldenCase golden, GameTestHelper helper, int repetitions) {
        this.golden = golden;
        this.helper = helper;
        this.level = helper.getLevel();
        BlockPos origin = golden.script().negative() ? TestTerrain.NEGATIVE : TestTerrain.POSITIVE;
        this.terrain = new TestTerrain(this.level, origin);
        this.repetitions = repetitions;
        this.columns = TrajectoryColumns.all(this.terrain.origin());
    }

    void start() {
        this.settings = PinnedSettings.pin(this.golden.frames().fixedSteps());
        this.terrain.forceChunks(true);
    }

    boolean isFinished() {
        return this.finished;
    }

    /** Why the run failed, or null. */
    String failure() {
        return this.failure;
    }

    List<GoldenFile> recordings() {
        return this.recordings;
    }

    void beforeLevelTick() {
        try {
            if (this.tick < 0) {
                this.terrain.awaitChunks();
                startRepetition();
                return;
            }
            Script script = this.golden.script();
            for (Script.Event event : script.events()) {
                if (event.tick() == this.tick) {
                    event.action().accept(this.vehicle);
                }
            }
            driveVehicle(script);
        } catch (RuntimeException e) {
            fail("tick " + this.tick + ": " + e);
        }
    }

    void afterLevelTick() {
        if (this.tick < 0 || this.finished) {
            return;
        }
        if (!this.terrain.isLoaded(this.vehicle.position())) {
            fail("the vehicle left the loaded course at tick " + this.tick + ": " + this.vehicle.position());
            return;
        }
        this.rows.add(TrajectoryColumns.row(this.tick, this.vehicle, this.columns));
        if (++this.tick >= this.golden.script().ticks()) {
            endRepetition();
        }
    }

    /** Called when the test ends early (timeout or another failure), so nothing leaks into later tests. */
    void abort() {
        if (!this.finished) {
            fail("aborted");
        }
    }

    private void startRepetition() {
        this.terrain.clearEntities();
        this.terrain.build();
        SeededRandoms.seedGlobal(this.level, GoldenCase.SEED);
        this.vehicle = spawnVehicle();
        this.pilot = null;
        this.assist = this.golden.script().assist() != null ? this.golden.script().assist().get() : null;
        this.rows = new ArrayList<>();
        this.tick = 0;
    }

    private AircraftEntity spawnVehicle() {
        VehicleSpec info = this.golden.info().get();
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            throw new IllegalStateException("definition " + this.golden.definition() + " is not loaded");
        }
        Script.Spawn spawn = this.golden.script().spawn();
        Vec3 at = this.terrain.absolute(spawn.position());
        AircraftEntity created = item.createAircraft(this.level, at.x, at.y, at.z, new ItemStack(item));
        if (created == null) {
            throw new IllegalStateException("could not create " + this.golden.definition());
        }
        created.initRotationYaw(spawn.yaw());
        SeededRandoms.seedVehicle(created, GoldenCase.SEED);
        created.fuel().setAmount(spawn.fuel() < 0 ? created.fuel().capacity() : spawn.fuel());
        created.engine().setThrottle(spawn.throttle());
        created.setDeltaMovement(spawn.velocity());
        this.settings.pinVehicle(created);
        this.level.addFreshEntity(created);
        return created;
    }

    private void driveVehicle(Script script) {
        if (script.boardAt() < 0 || this.tick < script.boardAt() || this.vehicle.isRemoved()) {
            return;
        }
        if (this.pilot == null) {
            this.pilot = Pilot.spawn(this.helper);
        }
        if (this.pilot.isAboard(this.vehicle)) {
            PilotInput input = script.inputAt(this.tick);
            if (this.assist != null) {
                input = this.assist.apply(this.vehicle, input);
            }
            this.pilot.fly(this.vehicle, input, this.golden.frames());
        } else if (this.vehicle.getRiddenByEntity() == null) {
            this.pilot.clickToBoard(this.vehicle);
        }
    }

    private void endRepetition() {
        this.recordings.add(new GoldenFile(GoldenHeader.of(this.golden, this.terrain),
                TrajectoryColumns.header(this.columns), List.copyOf(this.rows)));
        cleanUpRepetition();
        if (this.recordings.size() >= this.repetitions) {
            finish();
        } else {
            this.tick = -1;
        }
    }

    private void cleanUpRepetition() {
        if (this.pilot != null) {
            this.pilot.leave();
            this.pilot = null;
        }
        if (this.vehicle != null) {
            this.vehicle.discard();
            this.vehicle = null;
        }
        this.terrain.clearEntities();
    }

    private void fail(String message) {
        if (this.failure == null) {
            this.failure = message;
        }
        cleanUpRepetition();
        finish();
    }

    private void finish() {
        if (this.finished) {
            return;
        }
        this.finished = true;
        this.tick = -1;
        this.terrain.forceChunks(false);
        if (this.settings != null) {
            this.settings.restore();
        }
    }
}
