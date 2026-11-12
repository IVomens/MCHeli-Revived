package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.component.WreckComponent;
import com.ivomens.mcheli.vehicle.component.WreckMotion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A destroyed plane or ship rolls away from whichever side it was banked towards and noses down in
 * the air; on the ground it settles.
 */
final class FixedWingWreckTumble implements WreckMotion {

    @Override
    public void adjustTumble(WreckComponent wreck, AircraftEntity plane, RandomSource random) {
        float roll = plane.roll();
        boolean spinPositive = roll >= 0.0F ? roll <= 90.0F : roll <= -90.0F;
        wreck.setTumbleRoll((0.5F + random.nextFloat()) * (spinPositive ? 1 : -1));
    }

    @Override
    public void tick(WreckComponent wreck, AircraftEntity plane) {
        if (VehicleMotion.isClearBelow(plane, 3, 3)) {
            if (Mth.abs(plane.getXRot()) < 10.0F) {
                plane.setXRot(plane.getXRot() + wreck.tumblePitch());
            }
            float roll = Mth.abs(plane.roll());
            if (roll < 45.0F || roll > 135.0F) {
                plane.rollAngle().set(plane.roll() + wreck.tumbleRoll());
            }
        } else if (Mth.abs(plane.getXRot()) > 20.0F) {
            plane.setXRot(plane.getXRot() * 0.99F);
        }
    }
}
