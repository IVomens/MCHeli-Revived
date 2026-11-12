package com.ivomens.mcheli.core.sim.propulsion;

import java.util.function.BooleanSupplier;

/**
 * The throttle and reverse lever while one control step moves them.
 * <p>
 * Every setting goes through {@link #set}, which applies the engine-damage rule each time, exactly
 * as each separate write did in the legacy code: a crippled engine never lets the throttle rise,
 * not even back up to zero from a small negative overshoot.
 */
public final class ThrottleLever {

    private final BooleanSupplier engineCrippled;
    private double throttle;
    private float reverseThrust;
    private boolean wasPositive;
    private boolean reverseEngaged;

    /**
     * @param engineCrippled asked only when the throttle would rise, as the legacy check was
     */
    public ThrottleLever(double throttle, float reverseThrust, BooleanSupplier engineCrippled) {
        this.throttle = throttle;
        this.reverseThrust = reverseThrust;
        this.engineCrippled = engineCrippled;
    }

    public double throttle() {
        return this.throttle;
    }

    public float reverseThrust() {
        return this.reverseThrust;
    }

    /**
     * Whether any setting during this step was above zero. Each such setting woke a parked vehicle
     * in the legacy code, even when a later one brought the throttle back to zero.
     */
    public boolean wasPositive() {
        return this.wasPositive;
    }

    /** Whether the pilot pulled the reverse lever further this step; tracked vehicles steer then. */
    public boolean reverseEngaged() {
        return this.reverseEngaged;
    }

    void set(double target) {
        double value = target > this.throttle && this.engineCrippled.getAsBoolean() ? this.throttle : target;
        if (value > 0.0) {
            this.wasPositive = true;
        }
        this.throttle = value;
    }

    void add(double delta) {
        set(this.throttle + delta);
    }

    void setReverseThrust(float value) {
        this.reverseThrust = value;
    }

    void engageReverse(float value) {
        this.reverseThrust = value;
        this.reverseEngaged = true;
    }
}
