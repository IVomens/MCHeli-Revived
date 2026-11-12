package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.component.EngineSound;

/**
 * The engine fades towards the throttle, an open hatch is louder, and steering or braking revs it
 * up; the extra noise dies down once the keys are released.
 */
final class TrackSound implements EngineSound {

    private static final float FADE_STEP = 0.02F;

    private final AircraftEntity tank;
    private float loudness;
    /** Extra engine noise from steering and braking. */
    private float strain;

    TrackSound(AircraftEntity tank) {
        this.tank = tank;
    }

    @Override
    public float volume() {
        VehicleSpec info = this.tank.spec();
        return info != null && info.file().features().throttleUpDown() <= 0.0F ? 0.0F : this.loudness * 0.7F;
    }

    @Override
    public float pitch() {
        float throttlePitch = (float) (0.5 + this.tank.engine().throttle() * 0.5);
        float steeringPitch = (float) (0.5 + this.strain * 0.5);
        return Math.max(throttlePitch, steeringPitch);
    }

    @Override
    public String defaultName() {
        return "prop";
    }

    @Override
    public void tick() {
        AircraftEntity t = this.tank;
        float target = (float) t.engine().throttle();
        if (t.getRiddenByEntity() != null && (t.parts().canopy() == null || t.parts().canopyRotation() < 1.0F)) {
            target += 0.1F;
        }
        if (!t.keys().moveLeft() && !t.keys().moveRight() && !t.keys().throttleDown()) {
            this.strain *= 0.8F;
        } else {
            this.strain = Math.min(this.strain + 0.1F, 0.75F);
        }
        target = Math.max(target, this.strain);

        if (this.loudness < target) {
            this.loudness = Math.min(this.loudness + FADE_STEP, target);
        } else if (this.loudness > target) {
            this.loudness = Math.max(this.loudness - FADE_STEP, target);
        }
    }
}
