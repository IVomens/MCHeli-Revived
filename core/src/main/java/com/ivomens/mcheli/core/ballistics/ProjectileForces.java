package com.ivomens.mcheli.core.ballistics;

import org.joml.Vector3d;

/**
 * The forces on a projectile in one tick: the boost of a rocket motor, gravity, and the air drag of a
 * missile. Each method changes the motion it is given, in blocks per tick.
 * <p>
 * The arithmetic is written out rather than left to JOML's vector methods, so every result is the one the
 * game always computed, to the last bit: the golden trajectories depend on it.
 */
public final class ProjectileForces {

    private ProjectileForces() {
    }

    /** Whether the motor burns at this tick: strictly between its start and end tick. */
    public static boolean isBoosting(int tick, int startTick, int endTick) {
        return tick > startTick && tick < endTick;
    }

    /**
     * Speeds the projectile up along its flight direction.
     *
     * @return whether it was pushed; a projectile at rest has no direction to be pushed along
     */
    public static boolean boost(Vector3d motion, double boost) {
        double speed = Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
        if (speed > 0) {
            motion.x += motion.x / speed * boost;
            motion.y += motion.y / speed * boost;
            motion.z += motion.z / speed * boost;
            return true;
        }
        return false;
    }

    /** Adds gravity, which is negative for a projectile that falls. */
    public static void fall(Vector3d motion, double gravity) {
        motion.y += gravity;
    }

    /**
     * Slows the projectile down. Drag acts along the horizontal part of the flight direction only, so a
     * missile keeps its sink rate.
     */
    public static void drag(Vector3d motion, double drag) {
        if (drag == 0.0) {
            return;
        }
        double speed = Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
        if (speed > 0.0) {
            motion.x -= motion.x / speed * drag;
            motion.z -= motion.z / speed * drag;
        }
    }
}
