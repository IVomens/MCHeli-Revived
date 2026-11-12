package com.ivomens.mcheli.aircraft;

import net.minecraft.world.entity.Entity;

/**
 * Where the pilot looks for one frame: locked to the airframe, offset to a fixed seat angle, or free
 * where the vehicle lets the mouse move the head.
 *
 * @param fixed      the seat holds the view at a fixed angle to the airframe
 * @param fixedYaw   that angle's yaw offset
 * @param fixedPitch that angle's pitch offset
 * @param mouseX     the frame's sideways mouse movement, in the units of {@code Entity.turn}
 * @param mouseY     the frame's vertical mouse movement for {@code Entity.turn}; positive looks down
 */
public record RiderLook(boolean fixed, float fixedYaw, float fixedPitch, float mouseX, float mouseY) {

    /** A pilot holding the mouse still, as the golden harness flies. */
    public static final RiderLook STILL = new RiderLook(false, 0.0F, 0.0F, 0.0F, 0.0F);

    /** Keeps the view locked to the airframe unless the vehicle lets the mouse move it. */
    void turnWithAirframe(AircraftEntity vehicle, Entity rider) {
        float yawOffset = this.fixed ? this.fixedYaw : 0.0F;
        if (!vehicle.modes().viewFollowsYaw() && !this.fixed) {
            rider.turn(this.mouseX, 0.0F);
        } else {
            followYaw(vehicle, rider, yawOffset);
        }

        float pitchOffset = this.fixed ? this.fixedPitch : 0.0F;
        if (!vehicle.modes().viewFollowsPitch() && !this.fixed) {
            rider.turn(0.0F, this.mouseY);
        } else {
            rider.xRotO = vehicle.getXRot() + pitchOffset;
            rider.setXRot(vehicle.getXRot() + pitchOffset);
        }
    }

    /**
     * Turns the rider to the airframe's yaw. On a carrier the previous yaw is only shifted by whole
     * turns, so the view interpolates the short way round instead of spinning.
     */
    public static void followYaw(AircraftEntity vehicle, Entity rider, float yawOffset) {
        if (vehicle.getVehicle() == null) {
            rider.yRotO = vehicle.getYRot() + yawOffset;
        } else {
            if (vehicle.getYRot() - rider.getYRot() > 180.0F) {
                rider.yRotO += 360.0F;
            }
            if (vehicle.getYRot() - rider.getYRot() < -180.0F) {
                rider.yRotO -= 360.0F;
            }
        }
        rider.setYRot(vehicle.getYRot() + yawOffset);
    }
}
