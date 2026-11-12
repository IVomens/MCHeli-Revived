package com.ivomens.mcheli.vehicle.definition;

import com.ivomens.mcheli.core.sim.model.FixedWingHandling;
import com.ivomens.mcheli.core.sim.model.GroundVehicleHandling;
import com.ivomens.mcheli.core.sim.model.HandlingProfile;
import com.ivomens.mcheli.core.sim.model.RotorcraftHandling;
import com.ivomens.mcheli.core.sim.model.TankHandling;
import com.ivomens.mcheli.core.sim.model.VerticalForces;
import com.ivomens.mcheli.content.def.vehicle.Physics;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;

/** Reads the motion model values out of a vehicle file, once per load. */
final class HandlingProfiles {

    private HandlingProfiles() {
    }

    static HandlingProfile of(VehicleFile file) {
        Physics physics = file.physics();
        VerticalForces.Gravity gravity = new VerticalForces.Gravity(physics.gravity(), physics.gravityInWater());
        float speed = VehicleRules.speed(file);
        boolean reverse = file.features().canReverse();
        return switch (file.kind()) {
            case HELICOPTER -> new RotorcraftHandling(gravity, speed, physics.onGroundPitch(), physics.canFloat());
            // planes and ships; a swept wing changes the top speed in flight, see WingSweepDef
            case PLANE, SHIP -> new FixedWingHandling(gravity, speed, physics.motionFactor(),
                    physics.onGroundPitch(), physics.canMoveOnGround(), reverse, physics.canFloat());
            case TANK -> new TankHandling(gravity, speed, physics.motionFactor(), physics.canMoveOnGround(), reverse,
                    physics.canFloat());
            case VEHICLE -> new GroundVehicleHandling(gravity, speed, physics.canFloat(),
                    file.kindFeatures().canMove(), file.kindFeatures().canRotate());
        };
    }
}
