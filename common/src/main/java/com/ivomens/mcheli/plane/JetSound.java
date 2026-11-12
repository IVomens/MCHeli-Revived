package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.component.EngineSound;

/** The engine fades towards the throttle; an open cockpit is louder. */
final class JetSound implements EngineSound {

    private static final float FADE_STEP = 0.02F;

    private final AircraftEntity plane;
    private float loudness;

    JetSound(AircraftEntity plane) {
        this.plane = plane;
    }

    @Override
    public float volume() {
        VehicleSpec info = this.plane.spec();
        return info != null && info.file().features().throttleUpDown() <= 0.0F ? 0.0F : this.loudness * 0.7F;
    }

    @Override
    public float pitch() {
        return (float) (0.6 + this.plane.engine().throttle() * 0.4);
    }

    @Override
    public String defaultName() {
        return "plane";
    }

    @Override
    public void tick() {
        AircraftEntity p = this.plane;
        float target = (float) p.engine().throttle();
        if (p.getRiddenByEntity() != null && (p.parts().canopy() == null || p.parts().canopyRotation() < 1.0F)) {
            target += 0.1F;
        }
        if (this.loudness < target) {
            this.loudness = Math.min(this.loudness + FADE_STEP, target);
        } else if (this.loudness > target) {
            this.loudness = Math.max(this.loudness - FADE_STEP, target);
        }
    }
}
