package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.component.BodyComponent;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import com.ivomens.mcheli.vehicle.component.FuelComponent;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.vehicle.component.WeaponComponent;

/**
 * What a component may touch of <em>another</em> vehicle it meets in the world: a tanker fills its fuel
 * tank, an ammunition carrier its weapons, a repair vehicle its hull, and a vehicle looking for a rack
 * to ride its seats and body.
 * <p>
 * A component gets its own vehicle's collaborators through its constructor and never asks an entity for
 * a component (see {@link com.ivomens.mcheli.vehicle.component.VehicleComponent}). Interactions between
 * vehicles are the exception, and this is the one place that lists them.
 */
public final class VehiclePeers {

    private VehiclePeers() {
    }

    /** The fuel tank a tanker fills. */
    public static FuelComponent fuelTank(AircraftEntity peer) {
        return peer.fuel();
    }

    /** The weapons an ammunition carrier reloads. */
    public static WeaponComponent armory(AircraftEntity peer) {
        return peer.weapons();
    }

    /** The hull a repair vehicle mends. */
    public static DamageComponent hull(AircraftEntity peer) {
        return peer.damage();
    }

    /** The seats and racks of a vehicle that carries others. */
    public static SeatsComponent seats(AircraftEntity carrier) {
        return carrier.seats();
    }

    /** Where a carrier's racks are in the world. */
    public static BodyComponent body(AircraftEntity carrier) {
        return carrier.body();
    }
}
