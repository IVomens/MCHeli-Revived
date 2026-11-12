package com.ivomens.mcheli.api.vehicle;

import net.minecraft.world.entity.Entity;

/**
 * A vehicle (helicopter, plane, ship, tank or ground vehicle) as other code sees it: a read-only view of its
 * state. Every MCHeli vehicle entity implements it, so {@code entity instanceof Vehicle} tells a vehicle
 * apart; {@link com.ivomens.mcheli.api.McheliApi#vehicleOf} also finds the vehicle of a seat, a hit box or
 * a rider.
 *
 * <pre>{@code
 * if (entity instanceof Vehicle vehicle && !vehicle.isDestroyed()) {
 *     Entity pilot = vehicle.seats().riderOf(0);
 * }
 * }</pre>
 *
 * <p><b>Side and thread.</b> Both sides; read it on the thread of the entity's level. Nothing here changes
 * the vehicle. Values are those of the side the entity lives on; on a client they are the synced ones.
 *
 * <p><b>Stability.</b> Stable since API 1.0. Implemented by MCHeli only; a minor version may add methods.
 */
public interface Vehicle {

    /** The vehicle as the entity it is. */
    Entity entity();

    /** What kind of vehicle this is; fixed for the entity's lifetime. */
    VehicleKind kind();

    /** Whether it is flown remotely from a UAV station. */
    boolean isUAV();

    /** Whether it has been destroyed and is only a wreck. */
    boolean isDestroyed();

    /** The bank angle in degrees; yaw and pitch are the entity's own rotation. */
    float roll();

    /** The engine throttle every client sees, from 0 to 1. */
    double throttle();

    /** Who sits where. */
    Crew seats();

    /** How seekers see the vehicle: stealth and the countermeasures that are running. */
    Targetable signature();
}
