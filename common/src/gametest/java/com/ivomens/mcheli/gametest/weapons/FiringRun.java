package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.gametest.golden.PinnedSettings;
import com.ivomens.mcheli.gametest.golden.SeededRandoms;
import com.ivomens.mcheli.gametest.snapshot.EntitySeeding;
import com.ivomens.mcheli.gametest.snapshot.QuietWorld;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays one {@link FiringScenario} on the {@link WeaponRange}, once or twice in a row, from the
 * test's tick (which runs after the level has ticked): load the range (without spending ticks on
 * it, see {@link WeaponRange#awaitChunks}), build it and seed every generator, set up the mount and
 * the target, wait until the weapon is ready, fire, then record {@value FiringScenario#FLIGHT_TICKS} ticks.
 */
final class FiringRun {

    /** A weapon that is still not ready after this many ticks is fired anyway; the golden says so. */
    private static final int MAX_PREPARE_TICKS = 400;

    private enum Phase { LOADING, PREPARING, FLYING, FINISHED }

    private final FiringScenario scenario;
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final WeaponRange range;
    private final int repetitions;
    private final List<String> recordings = new ArrayList<>();

    private PinnedSettings settings;
    private Runnable randomTicks;
    /** Takes a countermeasure fitted for the case off the target's definition again. */
    private Phase phase = Phase.LOADING;
    private Mount mount;
    private Entity target;
    private ShotRecorder recorder;
    private List<String> lines;
    private int phaseTicks;
    private String failure;

    FiringRun(FiringScenario scenario, GameTestHelper helper, int repetitions) {
        this.scenario = scenario;
        this.helper = helper;
        this.level = helper.getLevel();
        this.range = new WeaponRange(this.level);
        this.repetitions = repetitions;
    }

    void start() {
        this.settings = PinnedSettings.pin();
        this.randomTicks = QuietWorld.pin(this.level);
        this.range.forceChunks(true);
    }

    boolean isFinished() {
        return this.phase == Phase.FINISHED;
    }

    List<String> recordings() {
        return this.recordings;
    }

    @Nullable
    String failure() {
        return this.failure;
    }

    /** Called once per server tick, after the level has ticked. */
    void tick() {
        try {
            switch (this.phase) {
                case LOADING -> {
                    this.range.awaitChunks();
                    setUp();
                }
                case PREPARING -> prepare();
                case FLYING -> fly();
                case FINISHED -> {
                }
            }
        } catch (RuntimeException e) {
            fail(this.phase + " tick " + this.phaseTicks + ": " + e);
        }
    }

    private void setUp() {
        this.range.clearEntities();
        this.range.build();
        SeededRandoms.seedGlobal(this.level, FiringScenario.SEED);
        EntitySeeding.start(FiringScenario.SEED);
        this.lines = new ArrayList<>();
        this.lines.add("modes: " + modeNames());
        this.target = spawnTarget();
        this.mount = createMount();
        if (this.mount == null) {
            this.lines.add("mount: none, the weapon cannot be created");
            endRepetition();
            return;
        }
        this.phase = Phase.PREPARING;
        this.phaseTicks = 0;
    }

    private void prepare() {
        holdTarget();
        boolean ready = this.mount.prepare();
        this.phaseTicks++;
        if (!ready && this.phaseTicks < MAX_PREPARE_TICKS) {
            return;
        }
        this.lines.add("mount: " + this.mount.describe());
        this.lines.add((ready ? "ready after " : "never ready, fired anyway after ") + this.phaseTicks + " ticks");
        this.lines.add("before shot: " + state(this.mount.weapons()));
        // seed again at the trigger, so the boarding and waiting before cannot shift any generator
        SeededRandoms.seedGlobal(this.level, FiringScenario.SEED);
        EntitySeeding.start(FiringScenario.SEED);
        this.recorder = new ShotRecorder(this.level, this.range.area(), this.lines);
        this.recorder.beforeShot();
        this.mount.fire(this.scenario.target() == FiringScenario.Target.NONE ? null : this.target);
        this.recorder.afterShot();
        this.lines.add("after shot: " + state(this.mount.weapons()));
        this.phase = Phase.FLYING;
        this.phaseTicks = 0;
    }

    private void fly() {
        this.recorder.afterTick();
        holdTarget();
        this.mount.tick();
        this.phaseTicks++;
        if (this.phaseTicks == FiringScenario.COUNTERMEASURE_TICK) {
            useCountermeasure();
        }
        if (this.phaseTicks >= FiringScenario.FLIGHT_TICKS) {
            this.lines.add("after " + FiringScenario.FLIGHT_TICKS + " ticks: " + state(this.mount.weapons()));
            endRepetition();
        }
    }

    /** The display name in every mode, from a fresh copy of the weapon so the mount is not disturbed. */
    private String modeNames() {
        WeaponMount weapon = WeaponMount.create(this.level, this.scenario.weapon(), Vec3.ZERO, 0.0F, 0.0F, false, null);
        if (weapon == null) {
            return "<weapon cannot be created>";
        }
        WeaponGroup set = WeaponGroup.of(this.level, weapon);
        List<String> names = new ArrayList<>();
        for (int mode = 0; mode < Math.max(1, weapon.stats().modes()); mode++) {
            names.add('"' + set.displayName() + '"');
            set.switchMode();
        }
        return String.join(", ", names);
    }

    @Nullable
    private Mount createMount() {
        ServerPlayer player = this.helper.makeMockServerPlayerInLevel();
        VehicleSpec carrier = this.scenario.carrierInfo();
        Mount created = carrier != null
                ? VehicleMount.create(this.level, carrier, this.scenario.weapon(), player, FiringScenario.SEED,
                        this.settings)
                : HandheldMount.create(this.level, this.scenario.weapon(), player);
        if (created == null) {
            player.server.getPlayerList().remove(player);
            player.discard();
        }
        return created;
    }

    @Nullable
    private Entity spawnTarget() {
        return switch (this.scenario.target()) {
            case NONE -> null;
            case AIR_DUMMY, GROUND_DUMMY -> {
                ArmorStand dummy = new ArmorStand(EntityType.ARMOR_STAND, this.level);
                Vec3 at = this.scenario.target().position();
                dummy.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
                dummy.setNoGravity(true);
                this.level.addFreshEntity(dummy);
                this.lines.add("target: armor stand at " + ShotRecorder.vec(dummy.position()));
                yield dummy;
            }
            case VEHICLE -> {
                VehicleSpec registered = this.scenario.targetVehicle().get();
                VehicleSpec info = this.scenario.countermeasure().fitted(registered);
                AircraftEntity vehicle = Vehicles.spawn(this.level, registered, this.scenario.target().position(),
                        180.0F, FiringScenario.SEED + 7);
                this.settings.pinVehicle(vehicle);
                vehicle.setSpec(info);
                this.lines.add("target: " + info.name() + " at " + ShotRecorder.vec(vehicle.position()) + ", chaff "
                        + info.armament().haveChaff() + ", aps " + info.file().features().aps().enabled() + " range "
                                + info.file().features().aps().range());
                yield vehicle;
            }
        };
    }

    /**
     * Keeps a target aircraft where it was placed: nobody flies it, and a missile treats a target
     * close above the ground as lost. The position the missile sees each tick is then exact.
     */
    private void holdTarget() {
        if (this.target instanceof AircraftEntity vehicle && !vehicle.isRemoved()) {
            Vec3 at = this.scenario.target().position();
            vehicle.setPos(at.x, at.y, at.z);
            vehicle.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void useCountermeasure() {
        if (!(this.target instanceof AircraftEntity vehicle)) {
            return;
        }
        boolean used = switch (this.scenario.countermeasure()) {
            case NONE -> false;
            case FLARE -> vehicle.countermeasures().useFlare(vehicle.spec().armament().flare().types[0]);
            case CHAFF -> vehicle.countermeasures().useChaff();
            case APS, IRON_CURTAIN -> vehicle.countermeasures().useAPS();
        };
        if (this.scenario.countermeasure() != FiringScenario.Countermeasure.NONE) {
            this.lines.add("t" + this.recorder.tick() + " target uses " + this.scenario.countermeasure() + ": " + used);
        }
    }

    private static String state(WeaponGroup weapons) {
        return "ammo " + weapons.ammo() + " reserve " + weapons.reserveAmmo() + " heat "
                + weapons.heat() + " cooldown " + weapons.cooldown() + " reload " + weapons.reloadTicks()
                + " name \"" + weapons.displayName() + "\"";
    }

    private void endRepetition() {
        cleanUp();
        StringBuilder text = new StringBuilder();
        this.lines.forEach(line -> text.append(line).append('\n'));
        this.recordings.add(text.toString());
        if (this.recordings.size() >= this.repetitions) {
            finish();
        } else {
            this.phase = Phase.LOADING;
        }
    }

    private void cleanUp() {
        if (this.recorder != null) {
            this.recorder.stop();
            this.recorder = null;
        }
        EntitySeeding.stop();
        if (this.mount != null) {
            this.mount.remove();
            this.mount = null;
        }
        if (this.target != null) {
            this.target.discard();
            this.target = null;
        }
        this.range.clearEntities();
    }

    private void fail(String message) {
        if (this.failure == null) {
            this.failure = message;
        }
        cleanUp();
        finish();
    }

    /** Called when the test ends early, so nothing leaks into later tests. */
    void abort() {
        if (this.phase != Phase.FINISHED) {
            fail("aborted");
        }
    }

    private void finish() {
        this.phase = Phase.FINISHED;
        this.range.forceChunks(false);
        if (this.settings != null) {
            this.settings.restore();
            this.settings = null;
        }
        if (this.randomTicks != null) {
            this.randomTicks.run();
            this.randomTicks = null;
        }
    }
}
