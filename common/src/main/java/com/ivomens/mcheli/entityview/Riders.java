package com.ivomens.mcheli.entityview;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Which vehicle an entity is in charge of, from what it rides. Pure functions of the entity's state. */
public final class Riders {

    private Riders() {
    }

    /**
     * The vehicle the entity is aboard: as its pilot, in one of its seats, or flying it from a remote
     * station. Null when it rides nothing of the sort.
     */
    @Nullable
    public static Vehicle vehicleOf(@Nullable Entity rider) {
        if (rider == null) {
            return null;
        }
        return switch (rider.getVehicle()) {
            case Vehicle vehicle -> vehicle;
            case VehicleSeat seat -> seat.getParent();
            case RemoteStation station -> station.getControlled();
            case null, default -> null;
        };
    }

    /** The entity the rider sees the world from: the vehicle it flies remotely, else itself. */
    public static Entity eyeOf(Entity rider) {
        if (rider.getVehicle() instanceof RemoteStation station && station.getControlled() != null) {
            return station.getControlled().entity();
        }
        return rider;
    }
}
