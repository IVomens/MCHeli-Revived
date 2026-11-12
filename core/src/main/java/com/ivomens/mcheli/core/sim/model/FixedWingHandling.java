package com.ivomens.mcheli.core.sim.model;

/**
 * Plane, ship and VTOL definition values the motion model reads.
 *
 * @param gravity         per-tick gravity in air and water
 * @param maxSpeed        top speed over the ground, after any swept-wing bonus
 * @param motionFactor    share of horizontal speed kept per tick on the ground and after moving
 * @param onGroundPitch   the pitch the airframe rests at on its gear
 * @param canMoveOnGround whether thrust works while resting on solid ground
 * @param reverseEnabled  whether the definition has reverse thrust
 * @param floats          whether the hull floats on water
 */
public record FixedWingHandling(VerticalForces.Gravity gravity, float maxSpeed, float motionFactor,
                                float onGroundPitch, boolean canMoveOnGround, boolean reverseEnabled,
                                boolean floats) implements HandlingProfile {

    /** The same handling with another top speed; swept wings change it as they move. */
    public FixedWingHandling withMaxSpeed(float speed) {
        return new FixedWingHandling(this.gravity, speed, this.motionFactor, this.onGroundPitch, this.canMoveOnGround,
                this.reverseEnabled, this.floats);
    }
}
