package com.ivomens.mcheli.core.sim.propulsion;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.port.SimSettings;

/**
 * Throttle with a reverse lever, shared by planes, ships and tanks. Holding throttle-down at zero
 * throttle pulls the reverse lever; throttle-up first releases it before the engine spools up.
 */
public final class ReversibleThrottle {

    private static final double SPOOL_RATE = 0.01;
    private static final double AUTO_DOWN_RATE = 0.005;
    private static final double REVERSE_PULL_RATE = 0.0025;
    private static final double REVERSE_RELEASE_PER_TICK = 0.8;
    private static final double BRAKE_REVERSE_RELEASE = 0.5;
    private static final double BRAKE_RATE = 0.02;

    private ReversibleThrottle() {
    }

    /**
     * Scales the spool-up step while the vehicle rides a carrier, so it follows the carrier's
     * speed. Planes and tanks group the float product differently, and float rounding makes the two
     * differ, so each type supplies its own.
     */
    @FunctionalInterface
    public interface CarrierScaling {

        CarrierScaling NONE = step -> step;

        float scale(float step);
    }

    /**
     * How one vehicle type's throttle responds.
     *
     * @param step              the definition's {@code throttleUpDown}
     * @param carrier           spool-up scaling while riding a carrier
     * @param reverseEnabled    the definition's {@code enableBack}
     * @param reverseGain       multiplies how fast the reverse lever is pulled
     * @param reverseCap        the furthest the reverse lever goes
     * @param pivotTurnThrottle planes spool up by themselves below this throttle to pivot on the
     *                          ground; negative infinity turns that off
     */
    public record Setup(float step, CarrierScaling carrier, boolean reverseEnabled, float reverseGain,
                        float reverseCap, float pivotTurnThrottle) {
    }

    /** The reverse lever springs back a little every tick. */
    public static void releaseReverse(ThrottleLever lever) {
        lever.setReverseThrust((float) (lever.reverseThrust() * REVERSE_RELEASE_PER_TICK));
    }

    /** The brake drops the reverse lever faster and closes the throttle. */
    public static void brake(ThrottleLever lever, float step) {
        lever.setReverseThrust((float) (lever.reverseThrust() * BRAKE_REVERSE_RELEASE));
        ThrottleModel.idleDown(lever, BRAKE_RATE * step);
    }

    /** Follows the keys; gunner mode leaves the throttle alone. */
    public static void pilot(ThrottleLever lever, ControlInput input, Setup setup, SimSettings settings) {
        if (input.autopilot()) {
            return;
        }
        float step = setup.step();
        boolean accelerate = input.throttleUp();
        if (input.isTurning() && lever.throttle() < setup.pivotTurnThrottle() && !accelerate
                && !input.throttleDown()) {
            // pivot turns on the ground need some thrust even without the throttle key
            accelerate = true;
            step *= 2.0F;
        }

        if (accelerate) {
            accelerate(lever, setup.carrier().scale(step), setup.reverseEnabled());
        } else if (input.throttleDown()) {
            decelerate(lever, step, setup);
        } else if (settings.autoThrottleDown() && lever.throttle() > 0.0) {
            lever.add(-AUTO_DOWN_RATE * step);
            if (lever.throttle() <= 0.0) {
                lever.set(0.0);
            }
        }
    }

    private static void accelerate(ThrottleLever lever, float step, boolean reverseEnabled) {
        if (reverseEnabled && lever.reverseThrust() > 0.0F) {
            lever.setReverseThrust((float) (lever.reverseThrust() - SPOOL_RATE * step));
            return;
        }
        lever.setReverseThrust(0.0F);
        if (lever.throttle() < 1.0) {
            lever.add(SPOOL_RATE * step);
        } else {
            lever.set(1.0);
        }
    }

    private static void decelerate(ThrottleLever lever, float step, Setup setup) {
        if (lever.throttle() > 0.0) {
            lever.add(-SPOOL_RATE * step);
            return;
        }
        lever.set(0.0);
        if (setup.reverseEnabled()) {
            float pulled = (float) (lever.reverseThrust() + REVERSE_PULL_RATE * step * setup.reverseGain());
            lever.engageReverse(Math.min(pulled, setup.reverseCap()));
        }
    }
}
