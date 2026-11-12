package com.ivomens.mcheli.core.sim.assist;

import com.ivomens.mcheli.core.sim.control.ThrottleCommand;
import com.ivomens.mcheli.core.sim.control.ThrottleServo;
import com.ivomens.mcheli.core.sim.model.HelicopterModel;

/**
 * The helicopter assist's altitude hold. When the pilot lets go of the throttle in flight it takes
 * the altitude a moment ahead as its target and works the throttle keys to stay there: it asks for
 * a climb rate in proportion to the altitude error, works out the throttle that climb needs from the
 * helicopter's vertical law ({@link HelicopterModel#throttleForClimb}), adds a damping term for the
 * climb rate it has not reached yet and a slow integral for whatever the law leaves out, and then
 * presses the keys towards that throttle ({@link ThrottleServo}).
 * <p>
 * The legacy auto throttle ({@code AutoThrottleDownHeli}) eases the throttle towards one half, which
 * is below the hovering throttle of every definition, so a helicopter left alone sinks. This hold
 * does not touch that rule: it only presses keys, as a pilot would, and is used only by the opt-in
 * assist.
 * <p>
 * One per piloted vehicle; it remembers its target from tick to tick. Call {@link #update} once per
 * game tick.
 */
public final class AltitudeHold {

    /** Climb rate asked for per block of altitude error, per tick. */
    private static final double CLIMB_PER_BLOCK = 0.04;
    /** The fastest climb or descent the hold asks for, blocks per tick. */
    private static final double MAX_CLIMB = 0.2;
    /** Throttle added per block per tick of climb rate still missing. */
    private static final double DAMPING = 1.2;
    /** Throttle per block of altitude error and tick, summed up while engaged. */
    private static final double INTEGRAL_GAIN = 0.0004;
    private static final double INTEGRAL_LIMIT = 0.15;
    /** On engaging, the target is where the current climb rate takes the helicopter in this many ticks. */
    private static final double CAPTURE_LEAD_TICKS = 8.0;

    private double target = Double.NaN;
    private double integral;

    /**
     * What the hold sees in one tick.
     *
     * @param engaged      whether it may fly: the assist is on, the helicopter is airborne and not in
     *                     hover mode, and the pilot is not working the throttle
     * @param altitude     height of the vehicle, blocks
     * @param climb        climb rate over the last tick, blocks per tick
     * @param throttle     throttle now, 0 to 1
     * @param gravityInAir the definition's gravity in air, per tick (negative)
     * @param keyStep      the definition's {@code throttleUpDown}, which scales what one tick of a key does
     */
    public record Reading(boolean engaged, double altitude, double climb, double throttle, float gravityInAir,
                          float keyStep) {
    }

    /** The throttle keys for this tick; {@link ThrottleCommand#NONE} and no target while not engaged. */
    public ThrottleCommand update(Reading reading) {
        if (!reading.engaged()) {
            this.target = Double.NaN;
            this.integral = 0.0;
            return ThrottleCommand.NONE;
        }
        if (Double.isNaN(this.target)) {
            this.target = reading.altitude() + reading.climb() * CAPTURE_LEAD_TICKS;
        }
        double error = this.target - reading.altitude();
        this.integral = Math.clamp(this.integral + INTEGRAL_GAIN * error, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
        double climb = Math.clamp(CLIMB_PER_BLOCK * error, -MAX_CLIMB, MAX_CLIMB);
        double wanted = HelicopterModel.throttleForClimb(reading.gravityInAir(), climb)
                + DAMPING * (climb - reading.climb()) + this.integral;
        return ThrottleServo.toward(reading.throttle(), Math.clamp(wanted, 0.0, 1.0),
                ThrottleServo.keyDeadBand(reading.keyStep()));
    }

    /** The altitude it holds, or NaN while not engaged. */
    public double target() {
        return this.target;
    }
}
