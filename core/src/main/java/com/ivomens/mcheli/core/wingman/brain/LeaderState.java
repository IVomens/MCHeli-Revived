package com.ivomens.mcheli.core.wingman.brain;

/**
 * The leader a follower flies formation on: usually the aircraft a player rides, but any entity can
 * lead (game tests use an armor stand).
 *
 * @param yaw              Minecraft yaw
 * @param roll             zero for an entity that is not an aircraft
 * @param throttle         the leader's throttle; NaN when the leader is not an aircraft
 * @param motion           the leader's motion this tick, which tells whether it has stopped
 * @param aircraft         the leader is an MCHeli aircraft, so followers can copy its landing gear
 * @param gearFolded       the leader's landing gear is up (aircraft only)
 * @param playerAtControls a player flies the leader; followers stop attacking when nobody does
 */
public record LeaderState(Point position, float yaw, float roll, double throttle, Point motion, boolean aircraft,
                         boolean gearFolded, boolean playerAtControls) {

    /** Below this speed (blocks/tick) the leader counts as stopped. */
    private static final double STOP_SPEED = 0.4;

    public boolean isStopped() {
        return this.motion.length() < STOP_SPEED;
    }

    /** The leader's throttle, or {@code fallback} for a leader that has none. */
    public double throttleOr(double fallback) {
        return this.aircraft ? this.throttle : fallback;
    }
}
