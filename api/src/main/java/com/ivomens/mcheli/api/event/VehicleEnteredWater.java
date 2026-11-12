package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A vehicle of any kind entered water (vanilla's {@code isInWater()}: the hull touches water) and has stayed in
 * it for half a second (10 ticks), so a ship rocking on waves or a plane skimming the surface does not fire it
 * over and over. {@link VehicleLeftWater} fires when it has been out for as long.
 *
 * <p>MCHeli only follows a vehicle's water contact while someone listens to this event or to
 * {@link VehicleLeftWater}: the first state it sees after a listener registers (or after the vehicle loads)
 * counts as where the vehicle already was, without an event. Wrecks and parked vehicles that nothing wakes are
 * not followed.
 *
 * <p>Server side, on the server thread, at the end of the vehicle's tick. Since API 1.1.
 */
public interface VehicleEnteredWater extends McheliEvent {

    Vehicle vehicle();
}
