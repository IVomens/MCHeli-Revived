package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A helicopter or plane that had taken off ({@link VehicleTookOff}) touched the ground or the water and has
 * stayed down for half a second (10 ticks), so a bounce on touchdown is not a landing yet. Wrecks, vehicles
 * riding another entity and the other vehicle kinds never fire it; a crash fires it when the vehicle comes to
 * rest before its hit points run out. The same following rules as {@link VehicleTookOff} apply.
 *
 * <p>Server side, on the server thread, at the end of the vehicle's tick. Since API 1.1.
 */
public interface VehicleLanded extends McheliEvent {

    Vehicle vehicle();

    /** Whether it came down on water rather than on a block. */
    boolean onWater();
}
