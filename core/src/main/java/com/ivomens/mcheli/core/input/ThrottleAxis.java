package com.ivomens.mcheli.core.input;

/**
 * A joystick axis read as a throttle. The server owns the throttle and only knows the throttle keys,
 * so the axis sets a target the keys then chase (see {@code core.sim.control.ThrottleServo}).
 * <ul>
 *   <li>{@link Mode#ABSOLUTE}: the axis is a throttle lever; its position is the target. Pushed
 *       forward (towards -1, as most levers and gamepad sticks report it) is full throttle.</li>
 *   <li>{@link Mode#INCREMENTAL}: the axis is a spring-loaded stick; deflecting it forward raises
 *       the target and back lowers it, at a rate in proportion to the deflection. Centred, the
 *       target stays where it was left; the throttle keys still work, and after them the target
 *       starts again from wherever they left the throttle ({@link #release}).</li>
 * </ul>
 * One per client, fed once per tick.
 */
public final class ThrottleAxis {

    public enum Mode { ABSOLUTE, INCREMENTAL }

    private double target = Double.NaN;

    /** The throttle a lever at {@code value} (-1 forward to 1 back) asks for, 0 to 1. */
    public static double leverPosition(float value) {
        return Math.clamp((1.0 - Math.clamp(value, -1.0F, 1.0F)) / 2.0, 0.0, 1.0);
    }

    /**
     * @param mode          how the axis is read
     * @param value         the axis value, shaped (for an incremental axis, past its dead zone)
     * @param throttle      the vehicle's throttle now, where an incremental target starts from
     * @param dtSeconds     time since the last update
     * @param ratePerSecond how fast a fully deflected incremental axis moves the target, throttle per second
     * @return the target throttle, or NaN while an incremental axis has not been moved yet
     */
    public double update(Mode mode, float value, double throttle, double dtSeconds, double ratePerSecond) {
        if (mode == Mode.ABSOLUTE) {
            this.target = leverPosition(value);
            return this.target;
        }
        if (value == 0.0F) {
            return this.target;
        }
        double from = Double.isNaN(this.target) ? throttle : this.target;
        this.target = Math.clamp(from - value * ratePerSecond * dtSeconds, 0.0, 1.0);
        return this.target;
    }

    /** The target of the last update, or NaN. */
    public double target() {
        return this.target;
    }

    /** Lets go of the throttle, e.g. when the joystick is unplugged or the pilot leaves the vehicle. */
    public void release() {
        this.target = Double.NaN;
    }
}
