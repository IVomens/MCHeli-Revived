package com.ivomens.mcheli.core.wingman.brain;

import javax.annotation.Nullable;

/**
 * Attack geometry and weapon release. Guided weapons (or an order asking for an AC-130 style orbit)
 * circle the target, bombs overfly it, everything else closes to a stand-off distance, breaks away and
 * comes back. Picking the target and pulling the trigger belong to the world; this only decides where
 * to fly and whether a shot is on.
 */
public final class CombatBrain {

    /** Aim point above the target's feet. */
    static final double AIM_HEIGHT = 1.5;
    /** Radians per tick around the target when orbit-attacking (2 degrees). */
    private static final double ATTACK_ORBIT_RATE = Math.toRadians(2.0);
    /** A bombing run aims this far past the target so the aircraft overflies it. */
    private static final double BOMB_OVERFLY_DIST = 80.0;
    /** Bombs are released within this horizontal distance of the target. */
    private static final float BOMB_RELEASE_DIST = 80.0f;
    /** Fixed forward weapons fire only with the nose within this many degrees of the target. */
    private static final float FIXED_WEAPON_MAX_YAW_ERROR = 20.0f;

    private CombatBrain() {
    }

    /** Where to fly while attacking {@code target}, kept clear of the leader. */
    static Point moveTarget(Perception p, Controls c, Assignment a, Point target, WeaponProfile weapon) {
        OrderProgress order = a.autopilot().order();
        boolean orbitAttack = order != null && order.order().orbitAttack();
        Point point;
        if (weapon.orbitFire || orbitAttack) {
            point = orbit(target, a.autopilot(), order, weapon, p.tuning());
        } else if (weapon == WeaponProfile.BOMB) {
            point = overfly(c, target, p.tuning());
        } else {
            point = approach(c, target, weapon, p.tuning());
        }
        return p.tuning().clearOfLeader(point, p.leader());
    }

    /** Circles the target and fires whenever in range; the nose follows the orbit, not the target. */
    private static Point orbit(Point target, Autopilot autopilot, @Nullable OrderProgress order, WeaponProfile weapon,
                               Tuning tuning) {
        double radius = order != null && order.order().orbitRadius() > 0
                ? order.order().orbitRadius() : weapon.standoff;
        return autopilot.orbit(target.x(), tuning.attackAltitude(target.y(), weapon.altitudeOffset), target.z(), radius,
                ATTACK_ORBIT_RATE);
    }

    /** Closes to the stand-off distance, then breaks away to one and a half times it and comes back. */
    private static Point approach(Controls c, Point target, WeaponProfile weapon, Tuning tuning) {
        Point self = c.position();
        double dx = target.x() - self.x();
        double dz = target.z() - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double y = tuning.attackAltitude(target.y(), weapon.altitudeOffset);
        if (dist > weapon.standoff) {
            double ratio = (dist - weapon.standoff) / dist;
            return new Point(self.x() + dx * ratio, y, self.z() + dz * ratio);
        }
        double norm = Math.max(dist, 0.1);
        double escape = weapon.standoff * 1.5;
        return new Point(target.x() - dx / norm * escape, y, target.z() - dz / norm * escape);
    }

    /** Aims past the target along the current heading, so the run passes right over it. */
    private static Point overfly(Controls c, Point target, Tuning tuning) {
        float facing = (float) Math.toRadians(c.yaw());
        return new Point(target.x() - Math.sin(facing) * BOMB_OVERFLY_DIST,
                tuning.attackAltitude(target.y(), WeaponProfile.BOMB.altitudeOffset),
                target.z() + Math.cos(facing) * BOMB_OVERFLY_DIST);
    }

    /**
     * Whether a shot at the target is on: within the weapon's range, and for bombs close to overhead,
     * for fixed forward weapons with the nose on target. Guided weapons fire from any direction.
     */
    public static boolean isShotOn(Point self, float selfYaw, Point target, WeaponProfile weapon) {
        double dx = target.x() - self.x();
        double dy = target.y() + AIM_HEIGHT - self.y();
        double dz = target.z() - self.z();
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > weapon.fireRange) {
            return false;
        }
        if (weapon == WeaponProfile.BOMB) {
            return horizontal <= BOMB_RELEASE_DIST;
        }
        if (weapon.fixedForward) {
            float yawError = Math.abs(Angles.yawTowards(dx, dz) - selfYaw);
            if (yawError > 180f) {
                yawError = 360f - yawError;
            }
            return yawError < FIXED_WEAPON_MAX_YAW_ERROR;
        }
        return true;
    }
}
