package com.ivomens.mcheli.core.wingman.brain;

import javax.annotation.Nullable;

import static com.ivomens.mcheli.core.wingman.brain.Angles.clamp;
import static com.ivomens.mcheli.core.wingman.brain.Angles.pitchTowards;
import static com.ivomens.mcheli.core.wingman.brain.Angles.stepTowards;
import static com.ivomens.mcheli.core.wingman.brain.Angles.wrapDegrees;
import static com.ivomens.mcheli.core.wingman.brain.Angles.yawTowards;

/**
 * Turns the airframe towards where it should go, within rate limits; MCHeli's physics then turn the
 * new attitude into velocity. Where to go is the attack geometry when attacking, the autopilot target
 * when autonomous, and the formation slot when following.
 */
final class Steering {

    private static final float MAX_YAW_RATE_PLANE = 3.0f;
    private static final float MAX_YAW_RATE_HELI = 4.0f;
    private static final float ATTACK_YAW_RATE_PLANE = 5.0f;
    private static final float ATTACK_YAW_RATE_HELI = 8.0f;
    private static final float MAX_PITCH_RATE = 3.0f;
    private static final float ATTACK_PITCH_RATE = 5.0f;
    private static final float MAX_ROLL_RATE = 5.0f;
    private static final float MAX_PITCH_UP = 25.0f;
    private static final float MAX_PITCH_DOWN = -20.0f;
    private static final float MAX_HELI_PITCH = 20.0f;
    /** Bank angle per degree of yaw change per tick in autonomous turns. */
    private static final float BANK_PER_YAW_STEP = 7.0f;
    private static final float MAX_BANK = 45f;
    /** A follower aligns its heading with the leader as it closes in within this distance of its slot. */
    private static final double HEADING_BLEND_DIST = 30.0;

    /**
     * In cruise, a target more than this many degrees off the nose is replaced by a lead point at this
     * angle, so the aircraft turns instead of having its thrust point against its velocity.
     */
    private static final float MAX_LEAD_ANGLE = 60f;
    private static final double LEAD_POINT_DIST = 40.0;
    private static final double LEAD_MIN_TARGET_DIST = 20.0;

    private Steering() {
    }

    /** The cruise yaw rate, which also holds a ridden autopilot heading after physics. */
    static float cruiseYawRate(Airframe airframe) {
        return airframe.isHelicopter() ? MAX_YAW_RATE_HELI : MAX_YAW_RATE_PLANE;
    }

    /** Yaw rate a follower turns with towards a stopped leader's heading. */
    static float hoverYawRate() {
        return MAX_YAW_RATE_HELI;
    }

    /** One tick of steering: yaw, bank and pitch towards the target, and the weapons' aim. */
    static void steer(Perception p, Controls c, Assignment a) {
        if (p.senses().consumeEscapeTick()) {
            return;
        }
        boolean inAttack = a.attack().isAttacking();
        Point target = inAttack ? p.senses().attackTarget() : null;
        WeaponProfile weapon = WeaponProfile.of(a.attack().weaponType());

        Point moveTarget = moveTarget(p, c, a, target, weapon);
        Point steerPoint = target != null ? new Point(target.x(), target.y() + CombatBrain.AIM_HEIGHT, target.z())
                : moveTarget;

        boolean heli = p.airframe().isHelicopter();
        float maxYawRate = inAttack ? (heli ? ATTACK_YAW_RATE_HELI : ATTACK_YAW_RATE_PLANE)
                : cruiseYawRate(p.airframe());
        float maxPitchRate = inAttack ? ATTACK_PITCH_RATE : MAX_PITCH_RATE;
        float currentYaw = c.yaw();

        if (steerPoint != null) {
            boolean following = !inAttack && a.follows(p);
            Point self = c.position();
            double dx = steerPoint.x() - self.x();
            double dy = steerPoint.y() - self.y();
            double dz = steerPoint.z() - self.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            float yawStep = steerYaw(p, c, following, dx, dz, horizontal, currentYaw, maxYawRate);
            steerRoll(p, c, a, following, yawStep);
            steerPitch(c, inAttack && weapon.fixedForward, heli, moveTarget, dy, horizontal, maxPitchRate);
            // weapons aim along the rider pitch, so it always points at the actual target
            c.aimWeaponsPitch(pitchTowards(dy, Math.max(horizontal, 1.0)));
        }

        repelSiblings(p, c, currentYaw, maxYawRate);
    }

    /** Where to fly: the attack geometry, the autopilot target or the formation slot. */
    private static @Nullable Point moveTarget(Perception p, Controls c, Assignment a, @Nullable Point target,
                                              WeaponProfile weapon) {
        if (target != null) {
            return CombatBrain.moveTarget(p, c, a, target, weapon);
        }
        if (a.autopilot().isAutonomous()) {
            return autopilotTarget(c, a.autopilot());
        }
        if (p.leader() != null) {
            return p.tuning().slotPosition(p.leader(), a.slot());
        }
        return null;
    }

    /**
     * The autopilot target, except in cruise when it is far off the nose: then a lead point at
     * {@link #MAX_LEAD_ANGLE} is used so the aircraft keeps flying forward through the turn. Ground
     * phases, runway work and hover need the exact target.
     */
    private static Point autopilotTarget(Controls c, Autopilot autopilot) {
        Point target = autopilot.target();
        FlightPhase phase = autopilot.phase;
        if (PhaseControl.isTaxiing(phase) || PhaseControl.setsOwnThrottle(phase) || phase == FlightPhase.ALIGN
                || autopilot.hoverMode) {
            return target;
        }
        Point self = c.position();
        double dx = target.x() - self.x();
        double dz = target.z() - self.z();
        if (Math.sqrt(dx * dx + dz * dz) <= LEAD_MIN_TARGET_DIST) {
            return target;
        }

        float currentYaw = c.yaw();
        float offNose = wrapDegrees(yawTowards(dx, dz) - currentYaw);
        if (Math.abs(offNose) <= MAX_LEAD_ANGLE) {
            return target;
        }
        double leadYaw = Math.toRadians(currentYaw + Math.signum(offNose) * MAX_LEAD_ANGLE);
        return new Point(self.x() - Math.sin(leadYaw) * LEAD_POINT_DIST, target.y(),
                self.z() + Math.cos(leadYaw) * LEAD_POINT_DIST);
    }

    /**
     * Yaw towards the steering point; skipped when it is nearly straight above or below (vertical VTOL
     * flight), where the heading is undefined and would spin. Close to its slot a follower matches the
     * leader's heading rather than pointing at the slot.
     *
     * @return the yaw change of this tick, used for banking
     */
    private static float steerYaw(Perception p, Controls c, boolean following, double dx, double dz,
                                  double horizontal, float currentYaw, float maxYawRate) {
        if (horizontal < 1.0) {
            return 0f;
        }
        float targetYaw = yawTowards(dx, dz);
        if (following) {
            float blend = (float) Math.min(1.0, horizontal / HEADING_BLEND_DIST);
            float leaderYaw = p.leader().yaw();
            targetYaw = leaderYaw + wrapDegrees(targetYaw - leaderYaw) * blend;
        }
        float yawStep = clamp(wrapDegrees(targetYaw - currentYaw), -maxYawRate, maxYawRate);
        c.setYaw(currentYaw + yawStep);
        c.aimWeapons(targetYaw);
        return yawStep;
    }

    /**
     * Followers copy the leader's roll. Autonomous planes bank into their turns; MCHeli's yaw grows in a
     * left turn and a positive roll banks left, so the bank simply follows the yaw step. No banking on
     * the ground, in hover or for helicopters.
     */
    private static void steerRoll(Perception p, Controls c, Assignment a, boolean following, float yawStep) {
        if (following) {
            c.setRoll(stepTowards(c.roll(), p.leader().roll(), MAX_ROLL_RATE));
        }
        Autopilot autopilot = a.autopilot();
        if (autopilot.isAutonomous() && !p.airframe().isHelicopter() && !autopilot.hoverMode
                && autopilot.phase != FlightPhase.TAKEOFF_ROLL && autopilot.phase != FlightPhase.ALIGN) {
            float bank = clamp(yawStep * BANK_PER_YAW_STEP, -MAX_BANK, MAX_BANK);
            c.setRoll(stepTowards(c.roll(), bank, MAX_ROLL_RATE));
        }
    }

    /**
     * Fixed forward weapons are aimed with the whole nose. Otherwise pitch only follows the altitude of
     * the movement target and guided weapons aim through the rider pitch.
     */
    private static void steerPitch(Controls c, boolean aimWithNose, boolean heli, @Nullable Point moveTarget,
                                   double dy, double horizontal, float maxPitchRate) {
        float targetPitch;
        if (aimWithNose) {
            targetPitch = clamp(pitchTowards(dy, Math.max(horizontal, 1.0)), MAX_PITCH_DOWN, MAX_PITCH_UP);
        } else {
            double climb = moveTarget != null ? moveTarget.y() - c.position().y() : dy;
            if (heli) {
                targetPitch = clamp(pitchTowards(climb, Math.max(horizontal, 0.1)), -MAX_HELI_PITCH, MAX_HELI_PITCH);
            } else {
                targetPitch = clamp(pitchTowards(climb, Math.max(horizontal, 1.0)), MAX_PITCH_DOWN, MAX_PITCH_UP);
            }
        }
        float currentPitch = c.pitch();
        c.setPitch(currentPitch + clamp(targetPitch - currentPitch, -maxPitchRate, maxPitchRate));
    }

    /**
     * Keeps wingmen of the same leader apart: when another one is closer than the formation spacing,
     * the heading is nudged away from it by up to half the yaw rate. This replaces the steering yaw of
     * the tick.
     */
    private static void repelSiblings(Perception p, Controls c, float currentYaw, float maxYawRate) {
        double minSpacing = p.tuning().minimumSpacing();
        if (minSpacing <= 0) {
            return;
        }
        Point self = c.position();
        double pushX = 0;
        double pushZ = 0;
        for (Point sibling : p.senses().siblings()) {
            double dx = self.x() - sibling.x();
            double dz = self.z() - sibling.z();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < minSpacing && dist > 0.01) {
                double strength = (minSpacing - dist) / minSpacing;
                pushX += dx / dist * strength;
                pushZ += dz / dist * strength;
            }
        }
        if (pushX == 0 && pushZ == 0) {
            return;
        }
        c.setYaw(stepTowards(currentYaw, yawTowards(pushX, pushZ), maxYawRate * 0.5f));
    }
}
