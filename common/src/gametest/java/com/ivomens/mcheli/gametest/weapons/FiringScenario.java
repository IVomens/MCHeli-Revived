package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.registry.ContentEntry;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * One recorded shot: which weapon, fired from which mount, at what, and what the target does
 * while the round is in flight.
 *
 * @param testName       the game test name (lower case letters, digits and underscores)
 * @param goldenPath     the file below {@code golden/}
 * @param weapon         the weapon definition name
 * @param carrier        the vehicle that carries the weapon, or null to hold it by hand
 * @param target         what stands downrange
 * @param targetVehicle  the parked target vehicle for {@link Target#VEHICLE}
 * @param countermeasure what the target vehicle does {@link #COUNTERMEASURE_TICK} ticks after launch
 */
record FiringScenario(String testName, String goldenPath, String weapon, Supplier<VehicleSpec> carrier,
                      Target target, Supplier<VehicleSpec> targetVehicle, Countermeasure countermeasure) {

    /** Level ticks recorded after the shot. */
    static final int FLIGHT_TICKS = 60;
    static final int COUNTERMEASURE_TICK = 5;
    /** Every generator a case touches starts from this seed. */
    static final long SEED = 20261106L;

    /**
     * What stands downrange. Air targets float 40 blocks up: an air-to-air missile drops a target
     * that is within its LockMinHeight (25 blocks for the bundled ones) of the ground.
     */
    enum Target {
        /** Nothing: unguided weapons. */
        NONE(Vec3.ZERO),
        /** A floating armor stand, the lock target of air-to-air missiles. */
        AIR_DUMMY(new Vec3(0.0, 40.0, 96.0)),
        /** An armor stand on the floor, the lock target of anti-tank missiles. */
        GROUND_DUMMY(new Vec3(0.0, 1.0, 96.0)),
        /** A vehicle held in the air (see {@code targetVehicle}), for countermeasures. */
        VEHICLE(new Vec3(0.0, 40.0, 96.0));

        private final Vec3 offset;

        Target(Vec3 offset) {
            this.offset = offset;
        }

        /** Where the target stands, in world coordinates. */
        Vec3 position() {
            return WeaponRange.at(this.offset.x, this.offset.y, this.offset.z);
        }
    }

    /**
     * What the target does. A target without chaff or an APS (no bundled vehicle has one) uses a copy of its
     * definition fitted with it.
     */
    enum Countermeasure {
        NONE,
        FLARE,
        CHAFF,
        APS,
        IRON_CURTAIN;

        /** The default APS range; exactly 100 turns the system into the iron curtain. */
        private static final int APS_RANGE = 8;
        private static final int IRON_CURTAIN_RANGE = 100;

        /** The definition with the countermeasure fitted, for the target vehicle to use instead of its own. */
        VehicleSpec fitted(VehicleSpec info) {
            AircraftFeatures features = info.file().features();
            return switch (this) {
                case CHAFF -> refit(info, features.withFlare(features.flare().withChaff(true)));
                case APS, IRON_CURTAIN -> refit(info, features.withAps(new AircraftFeatures.Aps(true,
                        features.aps().useTime(), features.aps().waitTime(),
                        this == APS ? APS_RANGE : IRON_CURTAIN_RANGE)));
                default -> info;
            };
        }

        private static VehicleSpec refit(VehicleSpec info, AircraftFeatures features) {
            VehicleFile file = info.file().withFeatures(features);
            ContentEntry<VehicleFile> entry = VehicleTypes.entry(info)
                    .orElseThrow(() -> new IllegalArgumentException("not a loaded vehicle: " + info));
            return VehicleSpec.of(new ContentEntry<>(entry.type(), entry.location(), entry.filePath(), entry.format(),
                    entry.author(), file));
        }
    }

    @Nullable
    VehicleSpec carrierInfo() {
        return this.carrier.get();
    }

    /** No target vehicle. */
    static Supplier<VehicleSpec> none() {
        return () -> null;
    }
}
