package com.ivomens.mcheli.core.sim.control;

/**
 * The pilot's controls for one tick.
 *
 * @param throttleUp   throttle-up key (forward for ground vehicles)
 * @param throttleDown throttle-down key (back or brake)
 * @param left         left key
 * @param right        right key
 * @param autopilot    the pilot handed over flying: helicopters hover, planes and tanks in gunner
 *                     mode hold their altitude
 */
public record ControlInput(boolean throttleUp, boolean throttleDown, boolean left, boolean right,
                           boolean autopilot) {

    /** Left alone; holding both side keys cancels them out. */
    public boolean leftOnly() {
        return this.left && !this.right;
    }

    public boolean rightOnly() {
        return this.right && !this.left;
    }

    /** -1 for left, 1 for right, 0 for none or both. */
    public float steering() {
        if (leftOnly()) {
            return -1.0F;
        }
        return rightOnly() ? 1.0F : 0.0F;
    }

    public boolean isTurning() {
        return this.left != this.right;
    }

    /** The same input with the side keys released. */
    public ControlInput withoutSideKeys() {
        return new ControlInput(this.throttleUp, this.throttleDown, false, false, this.autopilot);
    }

    /** The same input with the throttle keys released. */
    public ControlInput withoutThrottleKeys() {
        return new ControlInput(false, false, this.left, this.right, this.autopilot);
    }

    /** The same input with the throttle-up key held, as target drones fly. */
    public ControlInput withThrottleUp() {
        return new ControlInput(true, this.throttleDown, this.left, this.right, this.autopilot);
    }
}
