package com.ivomens.mcheli.entityview;

import com.ivomens.mcheli.api.vehicle.Vehicle;

import javax.annotation.Nullable;

/**
 * Something an operator sits at to fly a vehicle remotely, a UAV station. The operator rides the station;
 * the vehicle it controls is elsewhere. Read-only view; connecting and disconnecting are the core's.
 */
public interface RemoteStation {

    /** The vehicle flown from the station right now, or null. */
    @Nullable
    Vehicle getControlled();
}
