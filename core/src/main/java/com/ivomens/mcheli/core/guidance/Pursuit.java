package com.ivomens.mcheli.core.guidance;

import org.joml.Vector3d;

/**
 * Pursuit guidance: each tick a missile turns part of the way towards its aim point, optionally led ahead
 * of a moving target by the time it needs to get there. There is no proportional navigation; the legacy
 * missiles never had it.
 * <p>
 * The arithmetic is written out rather than left to JOML's vector methods, so every result is the one the
 * game always computed, to the last bit.
 */
public final class Pursuit {

    /** Below this speed a motion has no direction to turn from. */
    private static final double MIN_SPEED = 1.0E-6;
    /** Below this speed a missile's lead is computed from its nominal speed instead. */
    private static final double MIN_LEAD_SPEED = 1.0E-4;

    private Pursuit() {
    }

    /**
     * Turns the motion towards the unit direction (dirX, dirY, dirZ). Speed and heading are eased
     * separately: easing the velocity per axis would cut the chord of a hard turn and bleed speed.
     *
     * @param targetSpeed the speed the missile eases towards
     * @param maxSpeed    cap of the eased speed
     * @param turnFactor  fraction of the way to turn and accelerate this tick
     * @return the new motion
     */
    public static Vector3d turn(double mx, double my, double mz, double dirX, double dirY, double dirZ,
                                double targetSpeed, double maxSpeed, double turnFactor) {
        double speed = Math.sqrt(mx * mx + my * my + mz * mz);
        if (speed < MIN_SPEED) {
            return new Vector3d(
                    mx + (dirX * targetSpeed - mx) * turnFactor,
                    my + (dirY * targetSpeed - my) * turnFactor,
                    mz + (dirZ * targetSpeed - mz) * turnFactor);
        }
        double newSpeed = Math.min(speed + (targetSpeed - speed) * turnFactor, maxSpeed);
        double curX = mx / speed;
        double curY = my / speed;
        double curZ = mz / speed;
        double newX = curX + (dirX - curX) * turnFactor;
        double newY = curY + (dirY - curY) * turnFactor;
        double newZ = curZ + (dirZ - curZ) * turnFactor;
        double length = Math.sqrt(newX * newX + newY * newY + newZ * newZ);
        if (length < MIN_SPEED) {
            newX = dirX;
            newY = dirY;
            newZ = dirZ;
            length = 1.0;
        }
        return new Vector3d(newX / length * newSpeed, newY / length * newSpeed, newZ / length * newSpeed);
    }

    /**
     * Where to aim at a moving target: its position moved on by its motion for as long as the missile
     * needs to cover the distance at its current speed.
     *
     * @param missile       the missile's position
     * @param missileMotion the missile's motion per tick
     * @param nominalSpeed  the speed to assume while the missile is almost at rest
     * @param target        the target's position
     * @param targetMotion  the target's motion per tick
     * @return the led aim point
     */
    public static Vector3d lead(Vector3d missile, Vector3d missileMotion, double nominalSpeed, Vector3d target,
                                Vector3d targetMotion) {
        double speed = Math.sqrt(missileMotion.x * missileMotion.x + missileMotion.y * missileMotion.y
                + missileMotion.z * missileMotion.z);
        if (speed < MIN_LEAD_SPEED) speed = nominalSpeed;
        double dx = missile.x - target.x;
        double dy = missile.y - target.y;
        double dz = missile.z - target.z;
        double timeToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz) / speed;
        return new Vector3d(target.x + targetMotion.x * timeToTarget, target.y + targetMotion.y * timeToTarget,
                target.z + targetMotion.z * timeToTarget);
    }

    /**
     * The yaw a homing missile is given after it turned, from its new motion. It goes through
     * {@code 180 / PI} rather than {@code Math.toDegrees}, as it always did, which can differ from
     * {@code FlightAngles.yaw} in the last bit.
     */
    public static float headingYaw(double mx, double mz) {
        return (float) (Math.atan2(mz, mx) * 180.0 / Math.PI) - 90.0F;
    }
}
