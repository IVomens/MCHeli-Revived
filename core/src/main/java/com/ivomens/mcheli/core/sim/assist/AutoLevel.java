package com.ivomens.mcheli.core.sim.assist;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.math.Attitude;

/**
 * The helicopter assist's auto-level: while the pilot leaves the stick centred, it moves the stick to
 * bring pitch and roll back to level, as a pilot easing the cyclic would. A helicopter's stick yaws
 * whenever it rolls, so the roll correction comes with the opposite rudder and only rolls.
 * <p>
 * It works on the input, not on the airframe: the attitude model, its rate limits and the server's
 * checks see an ordinary stick.
 *
 * @param gain    stick per degree off level, in mouse units (degrees per second of turn rate)
 * @param centred the stick counts as centred within this deflection on both axes, in mouse units
 * @param limit   most stick the correction uses on each axis
 */
public record AutoLevel(float gain, float centred, float limit) {

    /** Levels with a time constant of about two thirds of a second, never faster than 20 degrees per second. */
    public static final AutoLevel DEFAULT = new AutoLevel(1.5F, 2.0F, 20.0F);

    /**
     * @param input    the pilot's stick for this step
     * @param attitude the airframe now
     * @return the stick with the correction added, or {@code input} itself while the pilot steers
     */
    public AttitudeInput apply(AttitudeInput input, Attitude attitude) {
        if (input.freeLook() || Math.abs(input.stickX()) > this.centred || Math.abs(input.stickY()) > this.centred
                || input.rudder() != 0.0F) {
            return input;
        }
        // positive pitch is nose down, and the stick pulled back (positive y) raises the nose
        float pitch = clamp(this.gain * attitude.pitch());
        // positive x rolls (and yaws) towards positive roll
        float roll = clamp(-this.gain * attitude.roll());
        if (pitch == 0.0F && roll == 0.0F) {
            return input;
        }
        return new AttitudeInput(input.stickX() + roll, input.stickY() + pitch, false, -roll);
    }

    private float clamp(float value) {
        return Math.clamp(value, -this.limit, this.limit);
    }
}
