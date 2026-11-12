package com.ivomens.mcheli.api.vehicle.data;

import java.util.function.UnaryOperator;

/**
 * The values addons keep on one vehicle, for changing them. They are saved with the vehicle; a change to a
 * synced key reaches the players that see the vehicle at the end of the server tick, together with the other
 * changes of that tick, and a player who starts seeing the vehicle gets every synced value.
 *
 * <pre>{@code
 * MutableVehicleData data = McheliApi.get().vehicleActions().control(vehicle).data();
 * data.update(KILLS, kills -> kills + 1);
 * }</pre>
 *
 * <p><b>Side and thread.</b> Server only, on the server thread.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface MutableVehicleData extends VehicleData {

    /**
     * Sets the vehicle's value for the key.
     *
     * @throws NullPointerException when the value is null; use {@link #remove} to clear it
     */
    <T> void set(VehicleDataKey<T> key, T value);

    /** Sets the value to what the function makes of the current one (the default when there is none). */
    default <T> T update(VehicleDataKey<T> key, UnaryOperator<T> change) {
        T value = change.apply(get(key));
        set(key, value);
        return value;
    }

    /** Forgets the vehicle's value, so it reads as the key's default again. Nothing happens when it has none. */
    void remove(VehicleDataKey<?> key);
}
