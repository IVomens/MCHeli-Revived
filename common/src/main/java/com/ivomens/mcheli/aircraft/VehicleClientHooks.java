package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.Services;

import javax.annotation.Nullable;

/**
 * What a vehicle needs from the client that shows it. A dedicated server shows no vehicle and gets
 * the defaults here.
 */
public interface VehicleClientHooks {

    VehicleClientHooks INSTANCE = Services.sided(VehicleClientHooks.class, new VehicleClientHooks() {
    });

    /** The player of the vehicle's engine sound, or null when the vehicle is not on a client level. */
    @Nullable
    default EngineSoundPlayer engineSound(AircraftEntity vehicle) {
        return null;
    }

    /** The vehicle definition was reloaded, so its models load again. */
    default void reloadModels(VehicleSpec vehicle) {
    }

    /** Keeps an engine sound playing, following the vehicle; updated every tick. */
    interface EngineSoundPlayer {

        void update();
    }
}
