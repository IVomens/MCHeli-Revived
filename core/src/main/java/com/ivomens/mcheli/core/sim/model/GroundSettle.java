package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.Attitude;

/**
 * How an airframe settles while it rests on the ground or floats: pitch eases towards the
 * definition's resting pitch ({@code onGroundPitch}) and roll towards level.
 */
public final class GroundSettle {

    /** Beyond this tilt an airframe is not settling, it is tipping over, and is left alone. */
    private static final float SETTLE_LIMIT = 40.0F;
    private static final float ROTORCRAFT_FACTOR = 0.9F;
    private static final float AFLOAT_PITCH_FACTOR = 0.8F;
    private static final float AFLOAT_ROLL_FACTOR = 0.9F;

    private GroundSettle() {
    }

    /** Pitch and roll both keep {@code factor} of their offset from rest. */
    public static Attitude level(Attitude attitude, float restPitch, float factor) {
        return new Attitude(attitude.yaw(), easePitch(attitude.pitch(), restPitch, factor), attitude.roll() * factor);
    }

    /** Helicopters settle unless already tipped past 40 degrees. */
    public static Attitude settleRotorcraft(Attitude attitude, float restPitch) {
        Attitude settled = attitude;
        if (Math.abs(settled.pitch()) < SETTLE_LIMIT) {
            settled = settled.withPitch(easePitch(settled.pitch(), restPitch, ROTORCRAFT_FACTOR));
        }
        if (Math.abs(settled.roll()) < SETTLE_LIMIT) {
            settled = settled.withRoll(settled.roll() * ROTORCRAFT_FACTOR);
        }
        return settled;
    }

    /** A floating plane or ship levels towards zero pitch, not its resting pitch. */
    public static Attitude levelAfloat(Attitude attitude) {
        Attitude settled = attitude.withPitch(attitude.pitch() * AFLOAT_PITCH_FACTOR);
        if (Math.abs(settled.roll()) < SETTLE_LIMIT) {
            settled = settled.withRoll(settled.roll() * AFLOAT_ROLL_FACTOR);
        }
        return settled;
    }

    private static float easePitch(float pitch, float restPitch, float factor) {
        return (pitch - restPitch) * factor + restPitch;
    }
}
