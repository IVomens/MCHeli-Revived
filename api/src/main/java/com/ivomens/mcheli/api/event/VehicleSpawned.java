package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A new vehicle entered a server level: placed from its item, by a dispenser, a command, a wingman order or
 * another mod. Fired after the entity was added. Vehicles that load with their chunk or arrive from another
 * dimension are not new; {@link com.ivomens.mcheli.api.McheliApi#vehicles} lists every loaded vehicle.
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface VehicleSpawned extends McheliEvent {

    Vehicle vehicle();
}
