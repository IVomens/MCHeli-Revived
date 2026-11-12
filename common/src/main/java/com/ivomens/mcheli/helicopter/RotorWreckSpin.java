package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.component.WreckComponent;
import com.ivomens.mcheli.vehicle.component.WreckMotion;
import net.minecraft.util.Mth;

/** A destroyed helicopter spins up around its mast as long as the rotor still turns, and tumbles in the air. */
final class RotorWreckSpin implements WreckMotion {

    private static final float MAX_SPIN = 15.0F;
    private static final float SPIN_UP = 0.3F;

    @Override
    public void tick(WreckComponent wreck, AircraftEntity heli) {
        if (wreck.tumbleYaw() < MAX_SPIN) {
            wreck.setTumbleYaw(wreck.tumbleYaw() + SPIN_UP);
        }
        heli.setYRot(heli.getYRot() + wreck.tumbleYaw() * (float) heli.engine().throttle());
        if (VehicleMotion.isClearBelow(heli, 3, 3)) {
            if (Mth.abs(heli.getXRot()) < 10.0F) {
                heli.setXRot(heli.getXRot() + wreck.tumblePitch());
            }
            heli.rollAngle().set(heli.roll() + wreck.tumbleRoll());
        }
    }
}
