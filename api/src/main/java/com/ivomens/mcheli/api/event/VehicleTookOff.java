package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A helicopter or plane left the ground (or the water it sat on) and has stayed in the air for a second
 * (20 ticks). Short hops and bounces on a runway do not count: the vehicle has to be off the ground for the
 * whole second, and {@link VehicleLanded} has to have come in between. Wrecks, vehicles riding another entity
 * and the other vehicle kinds never fire it.
 *
 * <p>MCHeli only follows a vehicle's ground contact while someone listens to this event or to
 * {@link VehicleLanded}: the first state it sees after a listener registers (or after the vehicle loads)
 * counts as where the vehicle already was, without an event. A parked vehicle that nothing wakes is not
 * followed.
 *
 * <p>Server side, on the server thread, at the end of the vehicle's tick. Since API 1.1.
 */
public interface VehicleTookOff extends McheliEvent {

    Vehicle vehicle();
}
