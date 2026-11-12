package com.ivomens.mcheli.core.math;

/** Angles in degrees: wrapping, differences and interpolation the way the vehicles always did them. */
public final class Degrees {

    private static final String[] COMPASS_8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final int COMPASS_8_STEP = 360 / COMPASS_8.length;

    private Degrees() {
    }

    /** The angle in [0, 360). */
    public static double wrap360(double degrees) {
        degrees %= 360.0;
        return degrees >= 0.0 ? degrees : degrees + 360.0;
    }

    /** The signed turn from {@code base} to {@code target}, at most half a turn either way. */
    public static double difference(double base, double target) {
        base = wrap360(base);
        target = wrap360(target);
        if (target - base < -180.0) {
            target += 360.0;
        } else if (target - base > 180.0) {
            base += 360.0;
        }
        return target - base;
    }

    /**
     * Interpolates a rotation between the previous and the current tick, taking the short way when
     * the two lie less than a turn and a half apart.
     */
    public static float lerpRotation(float partialTick, float previous, float current) {
        if (current - previous < -180.0F) {
            previous -= 360.0F;
        } else if (previous - current < -180.0F) {
            previous += 360.0F;
        }
        return previous + (current - previous) * partialTick;
    }

    /** The angle between the horizontal vectors (tx, tz) and (cx, cz). */
    public static float between(double tx, double tz, double cx, double cz) {
        double lengthA = Math.sqrt(tx * tx + tz * tz);
        double lengthB = Math.sqrt(cx * cx + cz * cz);
        double cosAngle = (tx * cx + tz * cz) / (lengthA * lengthB);
        return (float) (Math.acos(cosAngle) * 180.0 / Math.PI);
    }

    /** The compass point (S, SW, ... SE) of a yaw; 0 is south. */
    public static String compass8(int yaw) {
        yaw %= 360;
        if (yaw < 0) {
            yaw += 360;
        }
        return COMPASS_8[yaw / COMPASS_8_STEP];
    }
}
