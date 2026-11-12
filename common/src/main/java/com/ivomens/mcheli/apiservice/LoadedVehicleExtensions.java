package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.VehicleEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensions;
import com.ivomens.mcheli.extension.ConfiguredBehaviour;
import com.ivomens.mcheli.extension.ConfiguredMovement;
import com.ivomens.mcheli.extension.ExtensionRegistry;
import com.ivomens.mcheli.extension.VehicleAddons;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The API's view of {@link ExtensionRegistry} and of what each vehicle's file asks of it. */
final class LoadedVehicleExtensions implements VehicleExtensions {

    @Override
    public Set<ResourceLocation> behaviourTypes() {
        return ExtensionRegistry.get().behaviourIds();
    }

    @Override
    public Set<ResourceLocation> movementModels() {
        return ExtensionRegistry.get().movementIds();
    }

    @Override
    public List<ResourceLocation> behavioursOf(Vehicle vehicle) {
        return addonsOf(vehicle).behaviours().stream().<ResourceLocation>map(ConfiguredBehaviour::id).toList();
    }

    @Override
    public Optional<ResourceLocation> movementModelOf(Vehicle vehicle) {
        return Optional.ofNullable(addonsOf(vehicle).movement()).map(ConfiguredMovement::id);
    }

    private static VehicleAddons addonsOf(Vehicle vehicle) {
        VehicleSpec spec = vehicle.entity() instanceof VehicleEntity entity ? entity.spec() : null;
        return spec != null ? spec.file().addons() : VehicleAddons.NONE;
    }
}
