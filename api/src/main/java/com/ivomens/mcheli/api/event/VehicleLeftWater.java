package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A vehicle that had entered water ({@link VehicleEnteredWater}) has been out of it for half a second
 * (10 ticks). The same following rules as {@link VehicleEnteredWater} apply.
 *
 * <p>Server side, on the server thread, at the end of the vehicle's tick. Since API 1.1.
 */
public interface VehicleLeftWater extends McheliEvent {

    Vehicle vehicle();
}
