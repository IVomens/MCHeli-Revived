package com.ivomens.mcheli.api.vehicle;

import javax.annotation.Nullable;

/**
 * A passenger seat of a vehicle: an entity of its own, which the passenger rides and which follows the
 * vehicle. Weapons never hit seats; they hit the vehicle or the passenger.
 *
 * <p>Both sides, on the thread of the seat's level. Stable since API 1.0; implemented by MCHeli only, so a
 * minor version may add methods.
 */
public interface VehicleSeat {

    /** The vehicle the seat belongs to, or null before the client linked it. */
    @Nullable
    Vehicle getParent();
}
