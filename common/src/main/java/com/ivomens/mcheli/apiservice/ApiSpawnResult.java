package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnStatus;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

/** What a spawner did. */
record ApiSpawnResult(SpawnStatus status, Optional<Vehicle> vehicle, Optional<Component> message,
                      Map<Integer, ActionResult> riders) implements SpawnResult {

    static ApiSpawnResult failed(SpawnStatus status) {
        return new ApiSpawnResult(status, Optional.empty(), Optional.empty(), Map.of());
    }

    static ApiSpawnResult limitReached(Component message) {
        return new ApiSpawnResult(SpawnStatus.LIMIT_REACHED, Optional.empty(), Optional.of(message), Map.of());
    }

    static ApiSpawnResult spawned(Vehicle vehicle, Map<Integer, ActionResult> riders) {
        return new ApiSpawnResult(SpawnStatus.SPAWNED, Optional.of(vehicle), Optional.empty(), Map.copyOf(riders));
    }
}
