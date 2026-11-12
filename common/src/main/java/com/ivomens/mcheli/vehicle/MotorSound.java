package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.component.EngineSound;

/** Light vehicles have no sound of their own; a definition's engine sound follows the throttle. */
final class MotorSound implements EngineSound {

    private final AircraftEntity vehicle;

    MotorSound(AircraftEntity vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public float volume() {
        return (float) this.vehicle.engine().throttle() * 2.0F;
    }

    @Override
    public float pitch() {
        return (float) (this.vehicle.engine().throttle() * 0.5);
    }

    @Override
    public String defaultName() {
        return "";
    }
}
