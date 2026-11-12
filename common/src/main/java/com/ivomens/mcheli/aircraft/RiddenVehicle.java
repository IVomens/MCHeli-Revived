package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.uav.IUavStation;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** The vehicle an entity is aboard: as its pilot, in one of its seats, or flying it from a UAV station. */
public final class RiddenVehicle {

    private RiddenVehicle() {
    }

    @Nullable
    public static AircraftEntity of(@Nullable Entity rider) {
        if (rider == null) {
            return null;
        }
        Entity vehicle = rider.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (vehicle instanceof SeatEntity seat) {
            return seat.getParent();
        }
        if (vehicle instanceof IUavStation station) {
            return station.getControlled();
        }
        return null;
    }
}
