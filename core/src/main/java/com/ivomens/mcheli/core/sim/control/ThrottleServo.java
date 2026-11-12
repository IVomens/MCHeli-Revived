package com.ivomens.mcheli.core.sim.control;

/**
 * Moves a throttle the server owns towards a target with nothing but the throttle keys, as a pilot
 * would: up while it is too low, down while it is too high, and neither inside a dead band, so it
 * does not hunt around the target. The dead band should be at least half a key step.
 */
public final class ThrottleServo {

    private ThrottleServo() {
    }

    /**
     * A dead band for a vehicle's throttle keys: a little more than half of what one tick of the
     * faster key moves the throttle (the helicopter's up key, 0.02 per unit of {@code throttleUpDown}).
     *
     * @param keyStep the definition's {@code throttleUpDown}
     */
    public static double keyDeadBand(float keyStep) {
        return 0.6 * 0.02 * Math.max(keyStep, 0.0F);
    }

    /**
     * @param throttle the throttle now, 0 to 1
     * @param target   where it should be
     * @param deadBand how close counts as there
     */
    public static ThrottleCommand toward(double throttle, double target, double deadBand) {
        if (throttle < target - deadBand) {
            return ThrottleCommand.UP;
        }
        if (throttle > target + deadBand) {
            return ThrottleCommand.DOWN;
        }
        return ThrottleCommand.NONE;
    }
}
