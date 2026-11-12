package com.ivomens.mcheli.vehicle.sensor.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.content.def.vehicle.RWRType;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;

/**
 * The missiles homing on a vehicle, as its crew are told from the last threat table.
 * <p>
 * Which decoy fools a missile is known to a digital RWR, which identifies what it receives, and to a
 * flare or chaff dispenser's missile warning. An analog RWR ({@link RWRType#FOUR_WAY},
 * {@link RWRType#ALL_WAY_EARLY}) on its own only tells that a missile is coming, and from where.
 */
public final class IncomingMissileReader {

    private IncomingMissileReader() {
    }

    /** The missiles homing on the vehicle, counted by seeker when its equipment can tell them apart. */
    public static IncomingMissiles read(AircraftEntity vehicle) {
        return IncomingMissiles.of(RWRThreatClientTracker.getEvents(vehicle.getId()), identifiesSeekers(vehicle));
    }

    /** Whether the vehicle's warning equipment tells heat seekers from radar missiles. */
    public static boolean identifiesSeekers(AircraftEntity vehicle) {
        VehicleSpec spec = vehicle.spec();
        if (spec == null) {
            return false;
        }
        return hasDigitalRwr(vehicle) || vehicle.countermeasures().haveFlare() || vehicle.countermeasures().haveChaff();
    }

    /** Whether the vehicle has a digital RWR, which names the seeker of each missile on its scope. */
    public static boolean hasDigitalRwr(AircraftEntity vehicle) {
        VehicleSpec spec = vehicle.spec();
        return spec != null && spec.file().features().rwr().enabled()
                && spec.file().features().rwr().type() == RWRType.DIGITAL;
    }
}
