package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleClientHooks;
import com.ivomens.mcheli.aircraft.VehicleSpec;

import javax.annotation.Nullable;

/**
 * The engine's looping sound. The vehicle type's {@link EngineSound} says how loud and how high it
 * is; on a client the sound updater plays it, a server has none.
 */
public final class EngineSoundComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    @Nullable
    private final VehicleClientHooks.EngineSoundPlayer player;
    private EngineSound sound;

    public EngineSoundComponent(AircraftEntity vehicle, @Nullable VehicleClientHooks.EngineSoundPlayer player,
            EngineSound sound) {
        this.vehicle = vehicle;
        this.player = player;
        this.sound = sound;
    }

    public void setSound(EngineSound sound) {
        this.sound = sound;
    }

    public float volume() {
        return this.sound.volume();
    }

    public float pitch() {
        return this.sound.pitch();
    }

    /** The definition's engine sound, or the vehicle type's default. */
    public String name() {
        VehicleSpec info = this.vehicle.spec();
        if (info == null) {
            return "";
        }
        return info.file().sound().moveSound() != null ? info.file().sound().moveSound().getPath()
                : this.sound.defaultName();
    }

    /** Client: fades the sound towards what the engine does now. */
    public void tickFade() {
        this.sound.tick();
    }

    /** Keeps the loop playing, or stops it once the vehicle is gone. */
    public void play() {
        if (this.player != null) {
            this.player.update();
        }
    }
}
