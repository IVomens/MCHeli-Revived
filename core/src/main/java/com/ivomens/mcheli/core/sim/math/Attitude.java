package com.ivomens.mcheli.core.sim.math;

/**
 * Yaw, pitch and roll in degrees, as Minecraft stores them: yaw grows clockwise seen from above and
 * a negative pitch is nose up.
 */
public record Attitude(float yaw, float pitch, float roll) {

    public Attitude withYaw(float newYaw) {
        return new Attitude(newYaw, this.pitch, this.roll);
    }

    public Attitude withPitch(float newPitch) {
        return new Attitude(this.yaw, newPitch, this.roll);
    }

    public Attitude withRoll(float newRoll) {
        return new Attitude(this.yaw, this.pitch, newRoll);
    }
}
