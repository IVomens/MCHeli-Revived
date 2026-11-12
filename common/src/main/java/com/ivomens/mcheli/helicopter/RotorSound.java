package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.component.EngineSound;

/** The rotor gets louder and higher with the throttle; a helicopter without a throttle is silent. */
final class RotorSound implements EngineSound {

    private final AircraftEntity heli;

    RotorSound(AircraftEntity heli) {
        this.heli = heli;
    }

    @Override
    public float volume() {
        VehicleSpec info = this.heli.spec();
        return info != null && info.file().features().throttleUpDown() <= 0.0F ? 0.0F
                : (float) this.heli.engine().throttle() * 2.0F;
    }

    @Override
    public float pitch() {
        return (float) (0.2 + this.heli.engine().throttle() * 0.2);
    }

    @Override
    public String defaultName() {
        return "heli";
    }
}
