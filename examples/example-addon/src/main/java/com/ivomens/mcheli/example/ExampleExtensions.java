package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionRegistrar;
import net.minecraft.resources.ResourceLocation;

/**
 * Adds a movement model, {@code mcheli_example:hovercraft} ({@link HovercraftModel}), and a behaviour,
 * {@code mcheli_example:beacon} ({@link BeaconBehaviour}), that vehicle files can name. The pack's
 * {@code example_hovercraft} uses both. MCHeli finds this class through
 * {@code META-INF/services/com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider} before it reads any
 * vehicle file.
 */
public final class ExampleExtensions implements VehicleExtensionProvider {

    public static final ResourceLocation HOVERCRAFT = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID,
            "hovercraft");
    public static final ResourceLocation BEACON = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID,
            "beacon");

    @Override
    public void register(VehicleExtensionRegistrar registrar) {
        registrar.movementModel(HOVERCRAFT, HovercraftModel.Settings::decode, HovercraftModel::new);
        registrar.behaviour(BEACON, BehaviourTiming.AFTER_MOVEMENT, BeaconBehaviour.Settings::decode,
                BeaconBehaviour::new);
    }
}
