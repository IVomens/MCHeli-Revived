package com.ivomens.mcheli.core.sim.propulsion;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.port.SimSettings;

/** How a helicopter's collective follows the keys. */
public final class RotorThrottle {

    private static final double HOVER_SPOOL_UP = 1.0 / 30.0;
    private static final double SPOOL_UP_RATE = 0.02;
    private static final double SPOOL_DOWN_RATE = 1.0 / 70.0;
    /** Without keys the throttle settles between these, which roughly holds altitude. */
    private static final double SETTLE_HIGH = 0.52;
    private static final double SETTLE_LOW = 0.48;
    private static final double SETTLE_RATE = 0.01;
    /** Clients ease other players' rotors with a dead band just below the synced value. */
    private static final double FOLLOW_DEAD_BAND = 0.02;
    private static final double FOLLOW_RATE = 0.01;

    private RotorThrottle() {
    }

    /** Hovering spools the rotor up to full. */
    public static void hover(ThrottleLever lever) {
        if (lever.throttle() < 1.0) {
            lever.add(HOVER_SPOOL_UP);
        } else {
            lever.set(1.0);
        }
    }

    /**
     * @param step     the definition's {@code throttleUpDown}
     * @param settings with {@code autoThrottleDown} the throttle settles around half when no key is held
     */
    public static void pilot(ThrottleLever lever, ControlInput input, float step, SimSettings settings) {
        if (input.throttleUp()) {
            if (lever.throttle() < 1.0) {
                lever.add(SPOOL_UP_RATE * step);
            } else {
                lever.set(1.0);
            }
        } else if (input.throttleDown()) {
            ThrottleModel.idleDown(lever, SPOOL_DOWN_RATE * step);
        } else if (settings.autoThrottleDown()) {
            if (lever.throttle() > SETTLE_HIGH) {
                lever.add(-SETTLE_RATE * step);
            } else if (lever.throttle() < SETTLE_LOW) {
                lever.add(SETTLE_RATE * step);
            }
        }
    }

    public static void followSynced(ThrottleLever lever, double synced) {
        if (lever.throttle() >= synced - FOLLOW_DEAD_BAND) {
            lever.add(-FOLLOW_RATE);
        } else if (lever.throttle() < synced) {
            lever.add(FOLLOW_RATE);
        }
    }
}
