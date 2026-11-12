package com.ivomens.mcheli.core.sim.control;

/** What a throttle controller does with the throttle keys for one tick. */
public enum ThrottleCommand {
    /** Hold the throttle-up key. */
    UP,
    /** Hold the throttle-down key. */
    DOWN,
    /** Hold neither. */
    NONE;

    public boolean up() {
        return this == UP;
    }

    public boolean down() {
        return this == DOWN;
    }
}
