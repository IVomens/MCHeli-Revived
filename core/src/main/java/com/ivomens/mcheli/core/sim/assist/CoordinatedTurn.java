package com.ivomens.mcheli.core.sim.assist;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.math.Attitude;

/**
 * Auto-rudder for planes: a banked plane gets rudder towards the bank, so it turns the way it leans
 * instead of flying on straight with its wings tilted. The rudder grows with the sine of the bank,
 * so a knife edge turns hardest and an inverted plane gets none.
 *
 * @param gain    rudder at a 90 degree bank, as a share of full stick
 * @param minBank banks smaller than this, in degrees, get no rudder, so level flight stays untouched
 */
public record CoordinatedTurn(float gain, float minBank) {

    /** Full stick deflection in mouse units, the scale every stick source uses. */
    public static final float FULL_STICK = 40.0F;

    public static CoordinatedTurn withGain(float gain) {
        return new CoordinatedTurn(gain, 1.0F);
    }

    /**
     * @param input    the pilot's stick for this step
     * @param attitude the airframe now
     * @param airborne only a flying plane turns by banking; on the ground the input is left alone
     * @return the stick with the rudder added, or {@code input} itself
     */
    public AttitudeInput apply(AttitudeInput input, Attitude attitude, boolean airborne) {
        float roll = attitude.roll();
        if (!airborne || input.freeLook() || Math.abs(roll) < this.minBank || this.gain == 0.0F) {
            return input;
        }
        float rudder = (float) (this.gain * FULL_STICK * Math.sin(Math.toRadians(roll)));
        return new AttitudeInput(input.stickX(), input.stickY(), false, input.rudder() + rudder);
    }
}
