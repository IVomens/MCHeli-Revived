package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Vec3d;

/**
 * Car-like steering: the front wheels ramp towards full lock, and the hull turns at the rate a
 * bicycle with that wheel angle would at the current rolling speed. A stopped vehicle cannot turn.
 */
public final class BicycleSteering {

    /** Per-tick ramp towards a held steering key, in fractions of full lock. */
    private static final float ATTACK = 0.14F;
    /** Per-tick return to centre once released. */
    private static final float RELEASE = 0.20F;
    /** Per-tick ramp when flicking over to the opposite lock. */
    private static final float COUNTER = 0.30F;
    private static final float FULL_LOCK_DEGREES = 34.0F;
    /** Effective distance between front and rear axle, in blocks. */
    private static final float WHEELBASE = 3.0F;
    private static final float MIN_WHEEL_ANGLE = 1.0e-4F;
    private static final double MIN_SPEED = 1.0e-4;

    private BicycleSteering() {
    }

    /**
     * @param wheelAngle front wheel angle in -1..1 of full lock
     * @param yaw        the hull's yaw after turning
     */
    public record Steering(float wheelAngle, float yaw) {
    }

    public static Steering steer(float wheelAngle, float yaw, Vec3d velocity, ControlInput input) {
        float target = input.steering();
        float angle = approach(wheelAngle, target, rampRate(wheelAngle, target));
        double speed = GroundGrip.signedForwardSpeed(velocity, yaw);
        if (Math.abs(angle) > MIN_WHEEL_ANGLE && Math.abs(speed) > MIN_SPEED) {
            double yawRateRad = (speed / WHEELBASE) * Math.tan(Math.toRadians(angle * FULL_LOCK_DEGREES));
            return new Steering(angle, yaw + (float) Math.toDegrees(yawRateRad));
        }
        return new Steering(angle, yaw);
    }

    private static float rampRate(float wheelAngle, float target) {
        if (target == 0.0F) {
            return RELEASE;
        }
        return wheelAngle * target < 0.0F ? COUNTER : ATTACK;
    }

    /** Moves {@code current} towards {@code target} by at most {@code maxStep}, without overshooting. */
    private static float approach(float current, float target, float maxStep) {
        float delta = target - current;
        if (delta > maxStep) {
            return current + maxStep;
        }
        if (delta < -maxStep) {
            return current - maxStep;
        }
        return target;
    }
}
