package com.ivomens.mcheli.api.vehicle.action;

import com.ivomens.mcheli.api.content.VehicleInfo;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where addons change vehicles: place new ones ({@link #spawner}) and change existing ones
 * ({@link #control}). Reached through {@code McheliApi.get().vehicleActions()}.
 *
 * <p><b>Side and thread.</b> Server only, on the server thread.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleActions {

    /**
     * A spawner for a vehicle of the type with this kind and id (the content file's name, such as
     * {@code ah-64}). An unknown type is only reported when the spawner spawns
     * ({@link SpawnStatus#UNKNOWN_TYPE}).
     *
     * @param position where the vehicle's feet go: the bottom centre of its hull
     */
    VehicleSpawner spawner(ServerLevel level, VehicleKind kind, String typeId, Vec3 position);

    /** A spawner for a vehicle of the type; see {@link #spawner(ServerLevel, VehicleKind, String, Vec3)}. */
    default VehicleSpawner spawner(ServerLevel level, VehicleInfo type, Vec3 position) {
        return spawner(level, type.kind(), type.id(), position);
    }

    /**
     * A handle that changes the vehicle.
     *
     * @throws IllegalArgumentException when the vehicle lives in a client level
     */
    VehicleControl control(Vehicle vehicle);
}
