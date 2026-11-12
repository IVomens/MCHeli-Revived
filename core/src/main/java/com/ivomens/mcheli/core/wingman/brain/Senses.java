package com.ivomens.mcheli.core.wingman.brain;

import java.util.List;
import javax.annotation.Nullable;

/**
 * What a wingman learns from the world around it while it decides, and the few things it tells it.
 * The brain calls these in a fixed order; the game's implementation looks the answers up in the
 * level, tests script them.
 */
public interface Senses {

    /** Other loaded wingmen of the same leader (for autonomous aircraft: of no leader). */
    List<Point> siblings();

    /**
     * Counts down the straight flight after a heavy weapon was released.
     *
     * @return true while it runs, when the aircraft must not be steered this tick
     */
    boolean consumeEscapeTick();

    /** Feet of the current attack target, or null when there is none. May search the area for one. */
    @Nullable
    Point attackTarget();

    /** First free block above the ground at x/z; the bottom of the world where the chunk is not loaded. */
    double groundHeight(int x, int z);

    /** Whether a hostile mob is within this distance of the aircraft. */
    boolean hostileWithin(double radius);

    /** Hostile mobs within the radius of (x, z), at any height. */
    int hostilesAround(double x, double z, double radius);

    /** Shows the autopilot heading (NaN for none) and throttle to the player riding the aircraft. */
    void showAutopilot(float yaw, float throttle);

    /** The rider keeps the autopilot values they were last shown. */
    void keepAutopilotShown();

    /** Tells players near the aircraft how many hostiles recon counted, on the way home. */
    void reportRecon(int hostiles, String reason);
}
