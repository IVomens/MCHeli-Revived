package com.ivomens.mcheli.api.vehicle.data;

/**
 * The values addons keep on one vehicle, read-only: {@code vehicle.data().get(KEY)}. Change them on the
 * server through {@link MutableVehicleData}, which {@code McheliApi.get().vehicleActions().control(vehicle).data()}
 * returns.
 *
 * <p><b>Side and thread.</b> Both sides, on the thread of the vehicle's level. On the server it holds every
 * value; on a client only the values of synced keys ({@link VehicleDataKey#isSynced()}), as the server last
 * sent them, and the default for the rest.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleData {

    /** The vehicle's value for the key, or the key's default when it has none. Never null. */
    <T> T get(VehicleDataKey<T> key);

    /** Whether the vehicle has a value of its own for the key (even one equal to the default). */
    boolean has(VehicleDataKey<?> key);
}
