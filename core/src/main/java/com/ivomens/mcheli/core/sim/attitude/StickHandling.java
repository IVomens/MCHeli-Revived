package com.ivomens.mcheli.core.sim.attitude;

/**
 * How a definition answers the stick: the turn rate each axis may reach and the hard limits.
 *
 * @param yawRate   most yaw from the stick, degrees per second
 * @param pitchRate most pitch from the stick, degrees per second
 * @param rollRate  most roll from the stick, degrees per second
 * @param limits    the definition's pitch and roll limits
 */
public record StickHandling(float yawRate, float pitchRate, float rollRate, RotationLimits limits) {

    /** Stick rate of a definition with mobility 1, degrees per second. */
    private static final float BASE_RATE = 40.0F;

    /** Rates from the definition's {@code mobilityYaw}, {@code mobilityPitch} and {@code mobilityRoll}. */
    public static StickHandling of(float mobilityYaw, float mobilityPitch, float mobilityRoll, RotationLimits limits) {
        return new StickHandling(BASE_RATE * mobilityYaw, BASE_RATE * mobilityPitch, BASE_RATE * mobilityRoll, limits);
    }

    /** All three axes at full stick at once; generous, since real turns share the stick between axes. */
    public float fullStickRate() {
        return Math.abs(this.yawRate) + Math.abs(this.pitchRate) + Math.abs(this.rollRate);
    }
}
