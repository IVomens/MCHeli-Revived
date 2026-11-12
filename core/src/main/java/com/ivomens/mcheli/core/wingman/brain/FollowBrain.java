package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.sim.model.HelicopterModel;

import javax.annotation.Nullable;

import static com.ivomens.mcheli.core.wingman.brain.Angles.clamp;
import static com.ivomens.mcheli.core.wingman.brain.Angles.stepTowards;

/**
 * Formation flying on a leader. There are two states, decided afresh every tick:
 * <ul>
 *   <li><b>Holding</b>: the leader has stopped and the follower is within {@link #HOLD_DIST} of its
 *       slot. Planes close the throttle; helicopters come to a stop at the slot's height; both turn to
 *       the leader's heading.</li>
 *   <li><b>Closing</b>: otherwise. The steering aims at the slot and, after physics, a follower that fell
 *       behind is moved towards it (the catch-up boost).</li>
 * </ul>
 * How a follower keeps its speed and height depends on the airframe. A plane's throttle is its speed:
 * full far behind the slot, the leader's at the slot, less when ahead of it. A helicopter's throttle is
 * its lift, so it holds the slot's height with the collective and flies forward by tilting its nose
 * down, as far as the speed it needs to close on the slot asks.
 */
public final class FollowBrain {

    /** Within this distance of its slot a follower is in formation. */
    static final double SLOT_ARRIVAL_DIST = 5.0;
    /** When the leader has stopped, followers this close to their slot stop too. */
    private static final double HOLD_DIST = 25.0;
    /**
     * Plane throttle by the distance the slot lies ahead: full above the far distance, blending down to
     * the near one, and below it the leader's throttle trimmed by the distance ahead (or behind).
     */
    private static final double THROTTLE_BLEND_FAR = 80.0;
    private static final double THROTTLE_BLEND_NEAR = 12.0;
    private static final double THROTTLE_TRIM_PER_BLOCK = 0.01;
    /** Throttle assumed for a leader that has none (not an aircraft). */
    private static final double UNKNOWN_LEADER_THROTTLE = 0.5;

    /** Helicopter collective: climb rate asked for per block below the slot, blocks per tick. */
    private static final double CLIMB_PER_BLOCK = 0.04;
    private static final double MAX_CLIMB = 0.2;
    /** Throttle added per block per tick of climb rate still missing. */
    private static final double CLIMB_DAMPING = 1.2;
    /** Helicopter cyclic: closing speed asked for per block the slot lies ahead, and its limit. */
    private static final double CLOSE_PER_BLOCK = 0.04;
    private static final double MAX_CLOSING_SPEED = 0.3;
    /** Share of the speed the air takes each tick, which the tilt thrust has to make up. */
    private static final double DRAG_PER_TICK = 0.01;
    /** Share of the missing forward speed the cyclic asks to gain each tick. */
    private static final double SPEED_GAIN = 0.05;
    private static final float MAX_PITCH_RATE = 3.0f;
    private static final float MAX_HELI_PITCH = 20.0f;

    /**
     * The catch-up boost moves a follower up to this multiple of the leader's speed per tick, on top of
     * its own flight, so it can rejoin even when its top speed equals the leader's.
     */
    private static final double BOOST_LEADER_MULT = 2.0;
    private static final double BOOST_DIST_FULL = 80.0;
    /** The boost tapers off to nothing at the edge of the slot. */
    private static final double BOOST_DIST_OFF = SLOT_ARRIVAL_DIST;

    private FollowBrain() {
    }

    /** Before physics: throttle and steering towards the slot, or holding with a stopped leader. */
    static void fly(Perception p, Controls c, Assignment a) {
        LeaderState leader = p.leader();
        Point slot = p.tuning().slotPosition(leader, a.slot());
        double distToSlot = slot.distanceTo(c.position());
        boolean holding = leader.isStopped() && distToSlot < HOLD_DIST;
        float pitch = c.pitch();
        if (holding) {
            if (!p.airframe().isHelicopter()) {
                c.setThrottle(0.0);
            }
            c.setYaw(stepTowards(c.yaw(), leader.yaw(), Steering.hoverYawRate()));
        } else {
            if (!p.airframe().isHelicopter()) {
                c.setThrottle(throttle(leader, ahead(c, slot), distToSlot));
            }
            Steering.steer(p, c, a);
        }
        if (p.airframe().isHelicopter()) {
            c.setThrottle(collective(p.airframe(), c, slot));
            c.setPitch(cyclic(p.airframe(), c, leader, holding ? null : slot, pitch));
        }
    }

    /**
     * How far the slot lies ahead along the follower's nose (negative behind it). Far away the nose
     * points at the slot and this is the distance; close in the nose follows the leader's heading and
     * this is how far the follower trails its slot.
     */
    private static double ahead(Controls c, Point slot) {
        Point self = c.position();
        double yaw = Math.toRadians(c.yaw());
        return (slot.x() - self.x()) * -Math.sin(yaw) + (slot.z() - self.z()) * Math.cos(yaw);
    }

    /**
     * A plane's throttle: full when the slot is far ahead, the leader's throttle when level with it
     * (so it does not overshoot), and trimmed by the distance to go close in, down when the plane has
     * overshot. Some throttle always remains so a follower still reaches its slot when the leader is slow.
     */
    static double throttle(LeaderState leader, double ahead, double distToSlot) {
        double leaderThrottle = leader.throttleOr(UNKNOWN_LEADER_THROTTLE);
        double near = leaderThrottle + THROTTLE_BLEND_NEAR * THROTTLE_TRIM_PER_BLOCK;
        double throttle;
        if (ahead >= THROTTLE_BLEND_FAR) {
            throttle = 1.0;
        } else if (ahead > THROTTLE_BLEND_NEAR) {
            double t = (ahead - THROTTLE_BLEND_NEAR) / (THROTTLE_BLEND_FAR - THROTTLE_BLEND_NEAR);
            throttle = near + t * (1.0 - near);
        } else {
            throttle = leaderThrottle + ahead * THROTTLE_TRIM_PER_BLOCK;
        }
        double minThrottle = distToSlot > SLOT_ARRIVAL_DIST ? 0.25 : 0.05;
        return clamp(throttle, minThrottle, 1.0);
    }

    /**
     * A helicopter's collective holds the slot's height: it asks for a climb rate in proportion to the
     * height to go, takes the throttle that climb needs from the rotor's lift law and adds a damping term
     * for the climb rate not reached yet.
     */
    static double collective(Airframe airframe, Controls c, Point slot) {
        double climb = clamp(CLIMB_PER_BLOCK * (slot.y() - c.position().y()), -MAX_CLIMB, MAX_CLIMB);
        double throttle = HelicopterModel.throttleForClimb(airframe.gravity(), climb)
                + CLIMB_DAMPING * (climb - c.motion().y());
        return clamp(throttle, 0.0, 1.0);
    }

    /**
     * A helicopter's cyclic: the forward speed it wants is the leader's along its nose plus a closing
     * speed in proportion to how far the slot lies ahead. The rotor's tilt law gives the pitch whose
     * thrust holds that speed against the drag and closes the gap to it, within the pitch rate and
     * limit.
     *
     * @param slot  the slot to close on, or null to come to a stop with a stopped leader
     * @param pitch the pitch the helicopter has now, before steering touched it
     */
    static float cyclic(Airframe airframe, Controls c, LeaderState leader, @Nullable Point slot, float pitch) {
        double yaw = Math.toRadians(c.yaw());
        double noseX = -Math.sin(yaw);
        double noseZ = Math.cos(yaw);
        Point leaderMotion = leader.motion();
        double closing = slot == null ? 0.0
                : clamp(CLOSE_PER_BLOCK * ahead(c, slot), -MAX_CLOSING_SPEED, MAX_CLOSING_SPEED);
        double wanted = Math.max(0.0, leaderMotion.x() * noseX + leaderMotion.z() * noseZ + closing);
        double speed = c.motion().x() * noseX + c.motion().z() * noseZ;
        double acceleration = wanted * DRAG_PER_TICK + SPEED_GAIN * (wanted - speed);
        float target = HelicopterModel.pitchForAcceleration(acceleration, airframe.speedLimit(), c.throttle());
        if (!Float.isFinite(target)) {
            return pitch;
        }
        target = clamp(target, -MAX_HELI_PITCH, MAX_HELI_PITCH);
        return pitch + clamp(target - pitch, -MAX_PITCH_RATE, MAX_PITCH_RATE);
    }

    /**
     * After physics, while the leader flies: moves a follower that has fallen behind towards its slot, by
     * up to {@link #BOOST_LEADER_MULT} times the leader's speed, tapering off to nothing at the slot.
     * Only horizontal: changing Y directly makes MCHeli keep correcting upwards, so altitude is left to
     * pitch and throttle.
     */
    static void catchUp(Perception p, Controls c, Assignment a) {
        LeaderState leader = p.leader();
        Point slot = p.tuning().slotPosition(leader, a.slot());
        Point self = c.position();
        double dx = slot.x() - self.x();
        double dz = slot.z() - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= BOOST_DIST_OFF) {
            return;
        }
        double taper = dist >= BOOST_DIST_FULL ? 1.0 : (dist - BOOST_DIST_OFF) / (BOOST_DIST_FULL - BOOST_DIST_OFF);
        double boost = leader.motion().length() * BOOST_LEADER_MULT * taper;
        double norm = Math.max(dist, 0.01);
        c.setPosition(self.x() + dx / norm * boost, self.y(), self.z() + dz / norm * boost);
    }
}
