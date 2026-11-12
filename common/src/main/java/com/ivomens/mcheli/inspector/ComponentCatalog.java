package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.component.ScheduledStep;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every component of a vehicle the inspector can reach: those the vehicle hands out through its accessors,
 * plus the vehicle kind's own, which show up as the owners of their tick steps.
 */
final class ComponentCatalog {

    private ComponentCatalog() {
    }

    /** Each component once, with its class name, in a fixed order. */
    static Map<VehicleComponent, String> of(AircraftEntity v) {
        Map<VehicleComponent, String> all = new LinkedHashMap<>();
        List<Object> shared = List.of(v.identity(), v.lifecycle(), v.body(), v.physics(), v.parts(), v.runningGear(),
                v.keys(), v.rollAngle(), v.seats(), v.crewExit(), v.ropes(), v.ejectionSeats(), v.racks(), v.history(),
                v.uavLink(), v.tow(), v.cameras(), v.aim(), v.weapons(), v.fuel(), v.damage(), v.partBoxes(),
                v.wreck(), v.hullDamage(), v.ammoSupply(), v.searchLight(), v.radar(), v.appearance(), v.lighting(),
                v.sound(), v.collisionExclusions(), v.motionSync(), v.carrier(), v.boarding(), v.pilotBoarding(),
                v.pilotHandover(), v.seatChanges(), v.pilotPreferences(), v.stationShutdown(), v.modes(),
                v.engine(), v.rest(), v.scriptedPilot(), v.countermeasures(), v.statusWord());
        for (Object candidate : shared) {
            if (candidate instanceof VehicleComponent component) {
                all.putIfAbsent(component, component.getClass().getSimpleName());
            }
        }
        for (ScheduledStep step : v.tickSchedule()) {
            if (step.owner() != null) {
                all.putIfAbsent(step.owner(), step.owner().getClass().getSimpleName());
            }
        }
        return all;
    }
}
