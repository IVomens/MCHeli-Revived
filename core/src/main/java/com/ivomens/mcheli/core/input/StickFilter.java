package com.ivomens.mcheli.core.input;

/**
 * Optional softening of the mouse stick, both frame-rate independent.
 * <ul>
 *   <li>{@link #smooth}: the stick the airframe sees follows the mouse stick with a time constant,
 *       which takes the edge off a jerky mouse.</li>
 *   <li>{@link #recentre}: a stick that is left alone drifts back to the centre at a rate, so a
 *       stick-mode pilot does not have to find the centre by hand.</li>
 * </ul>
 * A time constant or rate of zero leaves the value exactly as it was, so a pilot with both options
 * off flies on the unaltered stick.
 */
public final class StickFilter {

    private float x;
    private float y;
    private boolean primed;

    /**
     * The smoothed stick for this frame.
     *
     * @param targetX       the stick to follow
     * @param targetY       the stick to follow
     * @param dtSeconds     frame length
     * @param timeConstant  seconds to cover about two thirds of the way; zero turns smoothing off
     * @return {x, y}
     */
    public float[] smooth(float targetX, float targetY, float dtSeconds, float timeConstant) {
        if (timeConstant <= 0.0F || !this.primed) {
            this.x = targetX;
            this.y = targetY;
            this.primed = true;
            return new float[]{targetX, targetY};
        }
        float follow = (float) (1.0 - Math.exp(-Math.max(0.0F, dtSeconds) / timeConstant));
        this.x += (targetX - this.x) * follow;
        this.y += (targetY - this.y) * follow;
        return new float[]{this.x, this.y};
    }

    /** Forgets the smoothed stick, e.g. for a new vehicle. */
    public void reset() {
        this.primed = false;
    }

    /**
     * The share of a stick left alone that remains after one frame of centring.
     *
     * @param ratePerSecond how fast it centres; after one second {@code exp(-rate)} of it is left.
     *                      Zero (or less) keeps all of it
     */
    public static double recentre(double ratePerSecond, double dtSeconds) {
        if (ratePerSecond <= 0.0) {
            return 1.0;
        }
        return Math.exp(-ratePerSecond * Math.max(0.0, dtSeconds));
    }
}
