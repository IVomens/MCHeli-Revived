package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.action.VehicleActions;
import com.ivomens.mcheli.api.vehicle.action.VehicleControl;
import com.ivomens.mcheli.api.vehicle.action.VehicleSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Spawners and control handles for the API; holds no state of its own. */
final class ApiVehicleActions implements VehicleActions {

    @Override
    public VehicleSpawner spawner(ServerLevel level, VehicleKind kind, String typeId, Vec3 position) {
        return new ApiVehicleSpawner(level, kind, typeId, position);
    }

    @Override
    public VehicleControl control(Vehicle vehicle) {
        if (!(vehicle.entity() instanceof AircraftEntity aircraft)) {
            throw new IllegalArgumentException(vehicle + " is not an MCHeli vehicle");
        }
        if (aircraft.level().isClientSide) {
            throw new IllegalArgumentException("Vehicles are changed on the server; " + aircraft + " is a client copy");
        }
        return new ApiVehicleControl(aircraft);
    }
}
