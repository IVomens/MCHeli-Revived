package com.ivomens.mcheli.core.wingman.brain;

/** Angle and range helpers in Minecraft's conventions (degrees, yaw 0 facing +Z). */
final class Angles {

    private Angles() {
    }

    /** Minecraft yaw that faces along (dx, dz). */
    static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Wraps an angle difference into [-180, 180]. */
    static float wrapDegrees(float degrees) {
        while (degrees > 180f) {
            degrees -= 360f;
        }
        while (degrees < -180f) {
            degrees += 360f;
        }
        return degrees;
    }

    /** Turns from {@code current} towards {@code target} by at most {@code maxStep} degrees. */
    static float stepTowards(float current, float target, float maxStep) {
        return current + clamp(wrapDegrees(target - current), -maxStep, maxStep);
    }

    /** Pitch (negative is up) that looks along a rise of {@code dy} over {@code run}. */
    static float pitchTowards(double dy, double run) {
        return (float) -Math.toDegrees(Math.atan2(dy, run));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
