package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleClientHooks;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.client.VehicleModelLoader;
import net.minecraft.client.Minecraft;

/** {@link VehicleClientHooks} on a physical client. */
public final class VehicleClientHooksService implements VehicleClientHooks {

    @Override
    public EngineSoundPlayer engineSound(AircraftEntity vehicle) {
        Minecraft mc = Minecraft.getInstance();
        return vehicle.level().isClientSide ? new AircraftSoundUpdater(mc, vehicle, mc.player) : null;
    }

    @Override
    public void reloadModels(VehicleSpec info) {
        VehicleModelLoader.load(info, true);
    }
}
