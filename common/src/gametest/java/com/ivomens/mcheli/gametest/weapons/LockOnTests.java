package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.gametest.golden.PinnedSettings;
import com.ivomens.mcheli.gametest.snapshot.TextGolden;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lock-on (docs/ARCHITECTURE.md Part 2, §5.5): which targets each seeker accepts, the lock cone,
 * and how the lock count builds up over ticks, against aircraft in the air and on the ground, a ship
 * in the water, animals, a stealthy plane and aircraft using flares or chaff. The results go to
 * {@code golden/weapons/lock_on.txt}.
 * <p>
 * {@code LockOnSeeker.lock} only runs on a client level, and a game test has none, so
 * {@link LockStepper} steps the seeker with {@code track}, which is {@code lock} without the tones.
 */
public class LockOnTests {

    private static final String GOLDEN = "weapons/lock_on.txt";
    /** Ticks the lock is held for in each progression. */
    private static final int LOCK_TICKS = 40;
    /** The aim swings away from the target at this tick in the "look away" progression. */
    private static final int LOOK_AWAY_TICK = 20;
    private static final int GRID_HALF_SPAN = 24;
    private static final int GRID_STEP = 4;
    /** Ticks for the targets to join the world; aircraft in the air sink a little, the golden says where to. */
    private static final int SETTLE_TICKS = 5;
    /** Height of the targets in the air: above the 25-block LockMinHeight of the bundled missiles. */
    private static final double AIR_HEIGHT = 41.0;
    /**
     * A plane with some stealth (0.3): the lock takes 30% longer and holds out to 70% of the range. The
     * stealthiest vehicle (stealth 1) can never be held, as in McHeli: its lock reach is zero.
     */
    private static final String PARTLY_STEALTHY = "f22a";

    public static List<TestFunction> lockOn() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction("mcheli_lock_on", "lock_on", "mcheli:empty",
                TextGolden.repetitions() * 1200, 0, true, LockOnTests::run));
    }

    private static void run(GameTestHelper helper) {
        LockOnRun run = new LockOnRun(helper);
        helper.onEachTick(() -> {
            run.tick();
            if (run.finished) {
                String problem = run.failure != null ? run.failure
                        : TextGolden.check(GOLDEN, header(), run.recordings);
                if (problem != null) {
                    throw new GameTestAssertException(GOLDEN + ": " + problem);
                }
                helper.succeed();
            }
        });
    }

    private static String header() {
        return "{\"schema\":1,\"range\":" + WeaponRange.describe() + ",\"pinned\":" + PinnedSettings.describe()
                + ",\"commit\":\"" + GoldenMode.commit() + "\"}";
    }

    /** One or two runs of the whole check list, driven from the test tick. */
    private static final class LockOnRun {

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final WeaponRange range;
        private final PinnedSettings settings = PinnedSettings.pin();
        private final List<String> recordings = new ArrayList<>();
        private final int repetitions = TextGolden.repetitions();
        private Map<String, Entity> targets;
        private ServerPlayer shooter;
        /** The flare carrier is fitted with chaff for the run, should it carry none. */
        private int ticks = -1;
        private boolean finished;
        private String failure;

        LockOnRun(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.range = new WeaponRange(this.level);
            this.range.forceChunks(true);
        }

        void tick() {
            if (this.finished) {
                return;
            }
            try {
                if (this.ticks < 0) {
                    this.range.awaitChunks();
                    setUp();
                    this.ticks = 0;
                } else if (++this.ticks == SETTLE_TICKS) {
                    this.recordings.add(check());
                    tearDown();
                    this.ticks = -1;
                    if (this.recordings.size() >= this.repetitions) {
                        finish();
                    }
                }
            } catch (RuntimeException e) {
                this.failure = "tick " + this.ticks + ": " + e;
                tearDown();
                finish();
            }
        }

        private void setUp() {
            this.range.clearEntities();
            this.range.build();
            this.shooter = this.helper.makeMockServerPlayerInLevel();
            Vec3 standing = WeaponRange.at(0.0, 1.0, 0.0);
            this.shooter.moveTo(standing.x, standing.y, standing.z, 0.0F, 0.0F);
            this.targets = new LinkedHashMap<>();
            VehicleSpec heli = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
            this.targets.put("aircraft in the air", vehicle(heli, 0.0, AIR_HEIGHT, 40.0));
            this.targets.put("aircraft on the ground", vehicle(heli, 8.0, 1.0, 40.0));
            this.targets.put("ship in the water", vehicle(Arsenal.firstVehicle(info ->
                    VehicleTypes.registry(VehicleKind.SHIP).get(info.name()).orElse(null) == info), 0.0, 0.5, 130.0));
            this.targets.put("stealthy plane in the air", vehicle(stealthiest(), -8.0, AIR_HEIGHT, 40.0));
            this.targets.put("partly stealthy plane in the air", vehicle(VehicleTypes.registry(VehicleKind.PLANE)
                    .get(PARTLY_STEALTHY).orElseThrow(), -24.0, AIR_HEIGHT, 40.0));
            VehicleSpec flareCarrier = Arsenal.firstVehicle(info -> info.armament().haveFlare());
            VehicleSpec withChaff = FiringScenario.Countermeasure.CHAFF.fitted(flareCarrier);
            this.targets.put("aircraft dropping flares", refitted(vehicle(flareCarrier, 12.0, AIR_HEIGHT, 40.0),
                    withChaff));
            this.targets.put("aircraft dropping chaff", refitted(vehicle(flareCarrier, -12.0, AIR_HEIGHT, 40.0),
                    withChaff));
            this.targets.put("pig on the ground", pig(16.0, 1.0, 40.0, false));
            this.targets.put("pig in the air", pig(16.0, AIR_HEIGHT, 40.0, true));
        }

        private String check() {
            ((AircraftEntity) this.targets.get("aircraft dropping flares")).countermeasures().useFlare(
                    ((AircraftEntity) this.targets.get("aircraft dropping flares")).spec().armament().flare().types[0]);
            ((AircraftEntity) this.targets.get("aircraft dropping chaff")).countermeasures().useChaff();
            StringBuilder text = new StringBuilder();
            text.append("targets:\n");
            this.targets.forEach((name, target) -> text.append("  ").append(name).append(": ")
                    .append(describe(target)).append('\n'));
            for (Seeker seeker : seekers()) {
                LockOnSeeker guidance = seeker.guidance();
                text.append(seeker.name()).append(" (lock range ").append(guidance.range())
                        .append(", angle ").append(guidance.angle()).append(", air ")
                        .append(guidance.filter().air()).append(", ground ").append(guidance.filter().ground())
                        .append(", water ").append(guidance.filter().water()).append(", min height ")
                        .append(guidance.filter().minHeight()).append(")\n");
                this.targets.forEach((name, target) -> text.append("  can lock ").append(name).append(": ")
                        .append(guidance.canLock(target, null)).append('\n'));
            }
            text.append(LockCone.grid(this.shooter, this.targets.get("pig on the ground"), GRID_HALF_SPAN, GRID_STEP));
            text.append(progressions());
            return text.toString();
        }

        private String progressions() {
            // each progression starts from a fresh seeker, as after the trigger was released
            StringBuilder text = new StringBuilder();
            text.append(progression("aim9 aimed at the aircraft in the air", "aircraft in the air", -1));
            text.append(progression("aim9 aimed at the stealthy plane", "stealthy plane in the air", -1));
            text.append(progression("aim9 aimed at the partly stealthy plane", "partly stealthy plane in the air",
                    -1));
            text.append(progression("aim9 looking away at tick " + LOOK_AWAY_TICK, "aircraft in the air",
                    LOOK_AWAY_TICK));
            text.append(progression("aim9 aimed at the pig in the air", "pig in the air", -1));
            text.append(progression("aim9 aimed at the aircraft dropping flares", "aircraft dropping flares", -1));
            return text.toString();
        }

        private String progression(String title, String target, int lookAwayTick) {
            return LockStepper.progression(title, seekers().getFirst().guidance(), this.shooter,
                    this.targets.get(target), LOCK_TICKS, lookAwayTick);
        }

        /** The seekers compared: two AA missiles as loaded and flagged, and an anti-tank missile. */
        private List<Seeker> seekers() {
            List<Seeker> seekers = new ArrayList<>();
            seekers.add(seeker("aim9 (as loaded)", "aim9", false, false));
            seekers.add(seeker("aim9 flagged as heat seeker", "aim9", true, false));
            seekers.add(seeker("aim120 flagged as radar missile", "aim120", false, true));
            String antiTank = Arsenal.firstWeapon(w -> "atmissile".equals(w.definition().type())).name();
            seekers.add(seeker(antiTank + " (as loaded)", antiTank, false, false));
            return seekers;
        }

        private Seeker seeker(String name, String weapon, boolean heatSeeker, boolean radar) {
            WeaponMount created = WeaponMount.create(this.level, weapon, Vec3.ZERO, 0.0F, 0.0F, false, null);
            LockOnSeeker guidance = created != null ? created.seeker() : null;
            if (guidance == null) {
                throw new IllegalStateException(weapon + " is not a seeker weapon");
            }
            if (heatSeeker) {
                guidance = guidance.withFilter(guidance.filter().withHeatSeeking(true));
            }
            if (radar) {
                guidance = guidance.withFilter(guidance.filter().withRadar(true));
            }
            return new Seeker(name, guidance);
        }

        private VehicleSpec stealthiest() {
            return Arsenal.vehicles().stream().max(Comparator.comparingDouble(info -> info.file().stealth())).orElseThrow();
        }

        private Entity vehicle(VehicleSpec info, double x, double y, double z) {
            AircraftEntity vehicle = Vehicles.spawn(this.level, info, WeaponRange.at(x, y, z), 180.0F,
                    FiringScenario.SEED);
            if (vehicle == null) {
                throw new IllegalStateException("could not spawn " + info.name());
            }
            this.settings.pinVehicle(vehicle);
            return vehicle;
        }

        private static Entity refitted(Entity vehicle, VehicleSpec info) {
            ((AircraftEntity) vehicle).setSpec(info);
            return vehicle;
        }

        private Entity pig(double x, double y, double z, boolean floating) {
            Pig pig = new Pig(EntityType.PIG, this.level);
            Vec3 at = WeaponRange.at(x, y, z);
            pig.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
            pig.setNoAi(true);
            pig.setNoGravity(floating);
            this.level.addFreshEntity(pig);
            return pig;
        }

        private static String describe(Entity target) {
            String name = target instanceof AircraftEntity ac ? ac.spec().name()
                    : target.getType().toShortString();
            Vec3 relative = target.position().subtract(Vec3.atLowerCornerOf(WeaponRange.ORIGIN));
            return name + " at " + ShotRecorder.vec(relative) + " onGround " + target.onGround() + " inWater "
                    + target.isInWater();
        }

        private void tearDown() {
            if (this.shooter != null) {
                this.shooter.server.getPlayerList().remove(this.shooter);
                this.shooter.discard();
                this.shooter = null;
            }
            this.range.clearEntities();
        }

        private void finish() {
            this.finished = true;
            this.range.forceChunks(false);
            this.settings.restore();
        }
    }

    private record Seeker(String name, LockOnSeeker guidance) {
    }
}
