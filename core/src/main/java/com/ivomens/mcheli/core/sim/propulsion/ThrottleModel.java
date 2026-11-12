package com.ivomens.mcheli.core.sim.propulsion;

/**
 * Throttle laws every vehicle type shares. How the keys move the throttle is type-specific, see
 * {@link RotorThrottle} and {@link ReversibleThrottle}.
 */
public final class ThrottleModel {

    /** The synced throttle travels in steps of this size. */
    private static final double SYNC_STEP = 0.05;
    /** How fast a client eases its copy of someone else's throttle towards the synced value. */
    private static final double FOLLOW_RATE = 0.005;
    private static final double WRECK_GROUNDED_SPOOL_DOWN = 0.8;
    private static final double WRECK_EXPLODED_SPOOL_DOWN = 0.98;

    private ThrottleModel() {
    }

    /** The throttle in sync steps; any positive throttle syncs as at least one step so the engine still sounds. */
    public static int toSyncSteps(double throttle) {
        int steps = (int) (throttle * 20.0);
        if (steps == 0 && throttle > 0.0) {
            steps = 1;
        }
        return steps;
    }

    public static double fromSyncSteps(int steps) {
        return SYNC_STEP * steps;
    }

    /** Unattended engines wind down by {@code rate} per tick and stop at zero. */
    public static void idleDown(ThrottleLever lever, double rate) {
        if (lever.throttle() > 0.0) {
            lever.add(-rate);
        } else {
            lever.set(0.0);
        }
    }

    /** A step that ran below zero ends at zero. */
    public static void clampAtZero(ThrottleLever lever) {
        if (lever.throttle() < 0.0) {
            lever.set(0.0);
        }
    }

    /**
     * Eases a client's copy of another player's throttle towards the synced value. The second test
     * sees the result of the first, so a copy just above the synced value can be nudged twice.
     */
    public static void followSynced(ThrottleLever lever, double synced) {
        if (lever.throttle() > synced) {
            lever.add(-FOLLOW_RATE);
        }
        if (lever.throttle() < synced) {
            lever.add(FOLLOW_RATE);
        }
    }

    /** A burning wreck's engine dies quickly once it is near the ground and slowly after it exploded. */
    public static void spoolDownWreck(ThrottleLever lever, boolean groundClose, boolean exploded) {
        if (groundClose) {
            lever.set(lever.throttle() * WRECK_GROUNDED_SPOOL_DOWN);
        }
        if (exploded) {
            lever.set(lever.throttle() * WRECK_EXPLODED_SPOOL_DOWN);
        }
    }
}
