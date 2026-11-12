package com.ivomens.mcheli.core.wingman.brain;

import static com.ivomens.mcheli.core.wingman.brain.Angles.clamp;
import static com.ivomens.mcheli.core.wingman.brain.Angles.wrapDegrees;

/**
 * A conventional takeoff from a runway, shared by node missions and mission orders:
 * <ul>
 *   <li>TAXI_OUT (node missions): straight to the threshold; within 30 blocks, ALIGN.</li>
 *   <li>ALIGN: stopped on the threshold, turning onto the runway heading; after settling for 20
 *       ticks, with the nose within 5 degrees and stopped (or after 80 ticks), TAKEOFF_ROLL.</li>
 *   <li>TAKEOFF_ROLL: full throttle down the centreline, held on the ground for 60 ticks; airborne in
 *       the rotation zone near the far end (or after 200 ticks), CLIMB.</li>
 *   <li>CLIMB: within 5 blocks of cruise altitude the takeoff is done.</li>
 * </ul>
 * Mission orders taxi out along their route instead ({@link OrderBrain}); node missions taxi straight
 * to the threshold. Before physics the phases set the throttle and the steering target; after physics
 * they hold the attitude MCHeli would otherwise recompute from the velocity vector.
 */
final class DepartureBrain {

    /** A node mission's straight taxi ends this close to the threshold. */
    private static final double TAXI_ARRIVE_DIST = 30.0;
    /** Heading error (degrees) accepted before starting the takeoff roll. */
    private static final double ALIGN_TOLERANCE = 5.0;
    /** Below this ground speed the aircraft counts as stopped on the threshold. */
    private static final double ALIGN_MAX_SPEED = 0.4;
    /** Above this height over the runway the aircraft is pushed down before aligning. */
    private static final double ALIGN_MAX_HEIGHT = 2.5;
    private static final double ALIGN_SINK_RATE = -0.25;
    /** Ticks to settle on the ground before the takeoff roll may start. */
    private static final int ALIGN_SETTLE_TICKS = 20;
    /** Ticks after which the roll starts even if the heading is still off. */
    private static final int ALIGN_TIMEOUT_TICKS = 80;
    /** How far ahead along the runway the aligning aircraft looks. */
    private static final double ALIGN_HEADING_DIST = 1000;

    /** Steering target distance ahead along the centreline. */
    static final double LOOKAHEAD_DIST = 40.0;
    /** Distance before RUNWAY_B where the nose starts to come up. */
    private static final double ROTATION_DIST = 50.0;
    private static final float MAX_ROTATION_PITCH = 20f;
    /** The aircraft is held on the ground for at least this many ticks of the roll. */
    private static final int MIN_GROUND_ROLL_TICKS = 60;
    /** Climb is forced after this many ticks even on a very short runway. */
    private static final int TAKEOFF_ROLL_TIMEOUT = 200;
    private static final double LIFTOFF_HEIGHT = 3.0;
    /** How far above the runway the roll holds the aircraft down, before it is pinned back. */
    private static final double GROUND_ROLL_SLACK = 0.3;
    /** Climb target is capped this far above the current altitude so the pitch stays reasonable. */
    private static final double CLIMB_STEP = 15.0;
    private static final float MIN_CLIMB_PITCH = -25f;
    private static final float MAX_CLIMB_PITCH = -3f;
    /** Within this many blocks of the target altitude the climb counts as done. */
    private static final double ALTITUDE_TOLERANCE = 5.0;

    private DepartureBrain() {
    }

    /**
     * A node mission's TAXI_OUT: straight to the threshold, where the taxi push moves the aircraft.
     * Other phases start it.
     *
     * @return true once the climb has reached {@code cruiseAlt}
     */
    static boolean flyFromThreshold(Perception p, Controls c, Autopilot autopilot, Runway runway, double cruiseAlt) {
        switch (autopilot.phase) {
            case TAXI_OUT -> {
                autopilot.steerTo(runway.ax(), runway.ay(), runway.az());
                c.setThrottle(0.0);
                autopilot.phaseTimer++;
                if (c.position().horizontalDistanceTo(runway.ax(), runway.az()) < TAXI_ARRIVE_DIST) {
                    autopilot.restart(FlightPhase.ALIGN);
                }
                return false;
            }
            case ALIGN, TAKEOFF_ROLL, CLIMB -> {
                return fly(p, c, autopilot, runway, cruiseAlt);
            }
            default -> {
                autopilot.restart(FlightPhase.TAXI_OUT);
                return false;
            }
        }
    }

    /**
     * One tick of ALIGN, TAKEOFF_ROLL or CLIMB; any other phase enters ALIGN.
     *
     * @return true once the climb has reached {@code cruiseAlt}, or at once on an unusable runway
     */
    static boolean fly(Perception p, Controls c, Autopilot autopilot, Runway runway, double cruiseAlt) {
        if (!runway.isUsable()) {
            return true;
        }
        switch (autopilot.phase) {
            case ALIGN -> align(p, c, autopilot, runway);
            case TAKEOFF_ROLL -> roll(p, c, autopilot, runway, cruiseAlt);
            case CLIMB -> {
                c.setThrottle(1.0);
                Point self = c.position();
                steerAlongCenterline(c, autopilot, runway, Math.min(cruiseAlt, self.y() + CLIMB_STEP));
                return self.y() >= cruiseAlt - ALTITUDE_TOLERANCE;
            }
            default -> autopilot.restart(FlightPhase.ALIGN);
        }
        return false;
    }

    private static void align(Perception p, Controls c, Autopilot autopilot, Runway runway) {
        c.setThrottle(0.0);
        Point self = c.position();
        autopilot.steerTo(self.x() + runway.dirX() * ALIGN_HEADING_DIST, runway.ay(),
                self.z() + runway.dirZ() * ALIGN_HEADING_DIST);

        // right after taxiing the aircraft can still be airborne (high spawn, VTOL lift); push it down and
        // do not start the settle timer until it is on the runway surface
        if (self.y() - runway.ay() > ALIGN_MAX_HEIGHT) {
            c.setMotionY(ALIGN_SINK_RATE);
            return;
        }

        autopilot.phaseTimer++;
        float runwayYaw = runway.yaw();
        float yawError = wrapDegrees(runwayYaw - c.yaw());
        boolean aligned = Math.abs(yawError) < ALIGN_TOLERANCE;
        boolean stopped = c.motion().horizontalLength() < ALIGN_MAX_SPEED;
        boolean settled = autopilot.phaseTimer >= ALIGN_SETTLE_TICKS;
        boolean timedOut = autopilot.phaseTimer > ALIGN_TIMEOUT_TICKS;
        if (settled && ((aligned && stopped) || timedOut)) {
            c.setYaw(runwayYaw);
            autopilot.groundRollY = self.y();
            autopilot.restart(FlightPhase.TAKEOFF_ROLL);
            p.log().debug("[Takeoff] {} ALIGN -> TAKEOFF_ROLL aligned={} timedOut={} yawError={}",
                    p.tag(), aligned, timedOut, yawError);
        }
    }

    /**
     * Full throttle down the centreline. The target altitude stays on the runway surface during the
     * ground roll and rises through the rotation zone just before RUNWAY_B, so the aircraft lifts off
     * near the far end like a real one.
     */
    private static void roll(Perception p, Controls c, Autopilot autopilot, Runway runway, double cruiseAlt) {
        c.setThrottle(1.0);
        c.setYaw(runway.yaw());
        autopilot.phaseTimer++;

        Point self = c.position();
        double distToEnd = runway.distanceToEnd(self);
        boolean groundRoll = autopilot.phaseTimer <= MIN_GROUND_ROLL_TICKS;
        if (distToEnd > ROTATION_DIST || groundRoll) {
            steerAlongCenterline(c, autopilot, runway, runway.ay());
        } else {
            double rotation = rotationFactor(distToEnd);
            steerAlongCenterline(c, autopilot, runway, runway.ay() + rotation * (cruiseAlt - runway.ay()));
        }

        boolean inRotation = distToEnd <= ROTATION_DIST;
        boolean airborne = self.y() > runway.ay() + LIFTOFF_HEIGHT;
        boolean rolledLongEnough = autopilot.phaseTimer >= MIN_GROUND_ROLL_TICKS;
        if ((inRotation && airborne && rolledLongEnough) || autopilot.phaseTimer > TAKEOFF_ROLL_TIMEOUT) {
            autopilot.enter(FlightPhase.CLIMB);
            p.log().debug("[Takeoff] {} TAKEOFF_ROLL -> CLIMB distToEnd={} timer={}",
                    p.tag(), (int) distToEnd, autopilot.phaseTimer);
        }
    }

    /** 0 at the start of the rotation zone, 1 at RUNWAY_B and beyond. */
    private static double rotationFactor(double distToEnd) {
        return clamp(1.0 - distToEnd / ROTATION_DIST, 0.0, 1.0);
    }

    /** Points the steering target {@link #LOOKAHEAD_DIST} ahead on the centreline, which also corrects drift. */
    private static void steerAlongCenterline(Controls c, Autopilot autopilot, Runway runway, double targetY) {
        double along = runway.distanceFromStart(c.position()) + LOOKAHEAD_DIST;
        autopilot.steerTo(runway.ax() + runway.dirX() * along, targetY, runway.az() + runway.dirZ() * along);
    }

    /**
     * After physics during the takeoff roll: keep the nose on the centreline, pin the aircraft to the
     * runway for the first part of the roll (MCHeli would otherwise lift it early) and raise the nose in
     * the rotation zone so the next physics tick produces lift.
     */
    static void holdRoll(Controls c, Autopilot autopilot, Runway runway) {
        if (!runway.isUsable()) {
            return;
        }
        c.setYaw(runway.yaw());

        Point self = c.position();
        if (autopilot.phaseTimer <= MIN_GROUND_ROLL_TICKS && autopilot.groundRollY > 0
                && self.y() > autopilot.groundRollY + GROUND_ROLL_SLACK) {
            c.setPosition(self.x(), autopilot.groundRollY, self.z());
            c.setMotionY(0);
        }

        double distToEnd = runway.distanceToEnd(c.position());
        if (distToEnd <= ROTATION_DIST && autopilot.phaseTimer > MIN_GROUND_ROLL_TICKS) {
            c.setPitch(-(float) (rotationFactor(distToEnd) * MAX_ROTATION_PITCH));
        }
    }

    /** After physics while climbing: force a nose-up pitch towards the climb target (at least 3 degrees). */
    static void holdClimbPitch(Controls c, Autopilot autopilot) {
        double dy = autopilot.targetY - c.position().y();
        float pitch = -(float) Math.toDegrees(Math.atan2(dy, LOOKAHEAD_DIST));
        c.setPitch(clamp(pitch, MIN_CLIMB_PITCH, MAX_CLIMB_PITCH));
    }
}
