package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;

/**
 * A definition's hard attitude limits ({@code RotationLimits}). Pitch and roll stop at the limits;
 * yaw is never limited.
 *
 * @param enabled  whether the definition limits rotation at all
 * @param minPitch most nose-up pitch
 * @param maxPitch most nose-down pitch
 * @param minRoll  furthest roll to the left
 * @param maxRoll  furthest roll to the right
 */
public record RotationLimits(boolean enabled, float minPitch, float maxPitch, float minRoll, float maxRoll) {

    public static final RotationLimits NONE = new RotationLimits(false, 0.0F, 0.0F, 0.0F, 0.0F);

    public float pitch(float pitch) {
        return LegacyMath.limit(pitch, this.minPitch, this.maxPitch);
    }

    public float roll(float roll) {
        return LegacyMath.limit(roll, this.minRoll, this.maxRoll);
    }

    /** The attitude with pitch and roll limited, when the definition asks for it. */
    public Attitude apply(Attitude attitude) {
        if (!this.enabled) {
            return attitude;
        }
        return new Attitude(attitude.yaw(), pitch(attitude.pitch()), roll(attitude.roll()));
    }
}
