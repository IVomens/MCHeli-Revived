package com.ivomens.mcheli.core.guidance;

import com.ivomens.mcheli.core.math.Degrees;

/**
 * The cone a lock-on seeker searches and holds its target in, and how a target's stealth (0 for none, 1
 * for invisible to seekers) narrows the cone, shortens the reach and slows the lock down.
 */
public final class LockCone {

    private LockCone() {
    }

    /**
     * Whether a target at the offset (dx, dy, dz) from the seeker lies within {@code lockAngle} degrees of
     * the aim, in yaw and in pitch separately (the game's degrees: yaw 0 faces south, pitch is positive
     * downwards).
     */
    public static boolean contains(double dx, double dy, double dz, float aimYaw, float aimPitch, float lockAngle) {
        float entityYaw = (float) Degrees.wrap360(aimYaw);
        float targetYaw = (float) Degrees.wrap360(Math.toDegrees(Math.atan2(dz, dx)));
        float yawDiff = (float) Degrees.wrap360(targetYaw - entityYaw - 90.0F);
        float targetPitch = -((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        float pitchDiff = targetPitch - aimPitch;
        return (yawDiff < lockAngle || yawDiff > 360.0F - lockAngle) && Math.abs(pitchDiff) < lockAngle;
    }

    /** The search cone for a target of this stealth: down to half of {@code angle} for an invisible one. */
    public static float searchAngle(int angle, float stealth) {
        float visibility = 1.0F - stealth;
        return angle * (visibility / 2.0F + 0.5F);
    }

    /** The distance a lock holds out to against a target of this stealth. */
    public static double reach(double range, float stealth) {
        return range * (1.0F - stealth);
    }

    /** Ticks to lock a target of this stealth: up to twice {@code lockTicks} for an invisible one. */
    public static int lockTicks(int lockTicks, float stealth) {
        return (int) (lockTicks + lockTicks * stealth);
    }
}
