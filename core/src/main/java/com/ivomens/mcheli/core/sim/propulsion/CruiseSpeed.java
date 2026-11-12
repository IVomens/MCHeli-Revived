package com.ivomens.mcheli.core.sim.propulsion;

/**
 * The cruise speed: a slowly following copy of the speed limit that rises while the vehicle
 * accelerates and sinks back otherwise.
 * <p>
 * It is never saved and never shown. Only the helicopter reads it, as the strength of its rotor
 * tilt thrust, so a helicopter loaded from disk starts slow until it has accelerated for a while.
 * Planes and tanks keep it up to date without using it.
 */
public final class CruiseSpeed {

    /** Where the cruise speed starts and the floor it decays to. */
    public static final double MINIMUM = 0.07;
    /** Ticks it takes to close most of the gap to the target. */
    private static final double RESPONSE_TICKS = 35.0;

    private CruiseSpeed() {
    }

    /** Moves the cruise speed towards {@code limit} while accelerating and back to the minimum otherwise. */
    public static double approach(double current, boolean accelerating, double limit) {
        if (accelerating && current < limit) {
            return Math.min(current + (limit - current) / RESPONSE_TICKS, limit);
        }
        return Math.max(current - (current - MINIMUM) / RESPONSE_TICKS, MINIMUM);
    }
}
