package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;

/**
 * A heavy gun rocking the hull after a shot: for the first ticks the hull tips away from the
 * direction of fire along a cosine swing, split between pitch and roll by where the gun points.
 *
 * @param ticksLeft ticks of recoil left; it counts down from the weapon's value
 * @param yaw       direction of fire relative to the hull, degrees
 * @param strength  peak tilt per tick, degrees
 */
public record RecoilKick(int ticksLeft, float yaw, float strength) {

    public static final RecoilKick NONE = new RecoilKick(0, 0.0F, 0.0F);

    /** Only the start of the countdown rocks the hull; the rest is the gun settling. */
    private static final int ROCKING_TICKS_LEFT = 12;
    /** Degrees of swing per tick left. */
    private static final int SWING_DEGREES_PER_TICK = 6;

    /** The attitude rocked by {@code ticks} ticks of recoil (a fraction for one frame). */
    public Attitude apply(Attitude attitude, float ticks) {
        if (this.ticksLeft < ROCKING_TICKS_LEFT) {
            return attitude;
        }
        float bearing = (float) ((this.yaw - attitude.roll()) * Math.PI / 180.0);
        float pitchShare = LegacyMath.cos(bearing);
        float rollShare = LegacyMath.sin(bearing);
        float swing = LegacyMath.cos((float) (this.ticksLeft * SWING_DEGREES_PER_TICK * Math.PI / 180.0)) * this.strength;
        return new Attitude(attitude.yaw(), attitude.pitch() + swing * pitchShare * ticks,
                attitude.roll() + swing * rollShare * ticks);
    }
}
