package com.ivomens.mcheli.core.sim.math;

/**
 * The game's float trigonometry, reproduced exactly.
 * <p>
 * Minecraft's {@code Mth.sin} and {@code Mth.cos} read a 65536-entry table instead of calling
 * {@link Math#sin}. The legacy movement code uses both kinds, and they differ in the last bits, so
 * every formula keeps the kind it always had.
 */
public final class LegacyMath {

    private static final int TABLE_SIZE = 65536;
    private static final float TABLE_STEPS_PER_RADIAN = 10430.378F;
    private static final float QUARTER_TURN_STEPS = 16384.0F;
    private static final float[] SINE_TABLE = buildSineTable();

    private LegacyMath() {
    }

    private static float[] buildSineTable() {
        float[] table = new float[TABLE_SIZE];
        for (int i = 0; i < table.length; i++) {
            table[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
        }
        return table;
    }

    /** Table sine, equal to {@code Mth.sin}. */
    public static float sin(float radians) {
        return SINE_TABLE[(int) (radians * TABLE_STEPS_PER_RADIAN) & (TABLE_SIZE - 1)];
    }

    /** Table cosine, equal to {@code Mth.cos}. */
    public static float cos(float radians) {
        return SINE_TABLE[(int) (radians * TABLE_STEPS_PER_RADIAN + QUARTER_TURN_STEPS) & (TABLE_SIZE - 1)];
    }

    /** Degrees to radians in float, the way the legacy code feeds angles to the sine table. */
    public static float floatRadians(float degrees) {
        return degrees / 180.0F * (float) Math.PI;
    }

    /** Unit vector along yaw and pitch, computed in float from the table (the legacy {@code Rot2Vec3}). */
    public static Vec3d lookVector(float yaw, float pitch) {
        float yawRad = floatRadians(yaw);
        float pitchRad = floatRadians(pitch);
        return new Vec3d(
                -sin(yawRad) * cos(pitchRad),
                -sin(pitchRad),
                cos(yawRad) * cos(pitchRad));
    }

    /** Forward unit vector on the ground plane, from {@link Math#sin} in double. */
    public static Vec3d flatForward(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }

    /** Same as {@code Mth.clamp(float, float, float)}. */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Same as {@code Mth.clamp(double, double, double)}. */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * The legacy range limit (the old {@code ModLib.RNG}). It tests the maximum first, so unlike
     * {@link #clamp(float, float, float)} it returns {@code max} when a definition gives a minimum
     * above its maximum.
     */
    public static float limit(float value, float min, float max) {
        return value > max ? max : Math.max(value, min);
    }

    public static double limit(double value, double min, double max) {
        return value > max ? max : Math.max(value, min);
    }

    /** Same as {@code Mth.wrapDegrees(float)}: the angle in {@code [-180, 180)}. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
