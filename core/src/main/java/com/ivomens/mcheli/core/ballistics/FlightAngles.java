package com.ivomens.mcheli.core.ballistics;

/**
 * The yaw and pitch, in the game's degrees, that point a projectile along its motion: yaw 0 faces south
 * (+z) and grows clockwise seen from above, pitch is positive downwards.
 */
public final class FlightAngles {

    /** Rotations are only taken from a motion longer than this, squared. */
    private static final double MIN_MOTION_SQR = 1.0E-6;

    private FlightAngles() {
    }

    /** Whether the motion is long enough to take a direction from. */
    public static boolean hasDirection(double mx, double my, double mz) {
        double horizontal = Math.sqrt(mx * mx + mz * mz);
        return horizontal * horizontal + my * my > MIN_MOTION_SQR;
    }

    /** Yaw along the horizontal motion. */
    public static float yaw(double mx, double mz) {
        return (float) Math.toDegrees(Math.atan2(mz, mx)) - 90.0F;
    }

    /** Pitch along the motion: negative while it climbs. */
    public static float pitch(double mx, double my, double mz) {
        double horizontal = Math.sqrt(mx * mx + mz * mz);
        return -(float) Math.toDegrees(Math.atan2(my, horizontal));
    }
}
