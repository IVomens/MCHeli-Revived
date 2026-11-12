package com.ivomens.mcheli.core.wingman.brain;

import static com.ivomens.mcheli.core.wingman.brain.Angles.clamp;

/**
 * The landing circuit onto a runway, shared by node missions and mission orders. The pattern enters
 * abeam RUNWAY_B, flies the downwind leg back past RUNWAY_A, turns base onto a final that touches down
 * at A and rolls out towards B:
 * <ul>
 *   <li>DESCEND to circuit height, abeam B: CIRCUIT_DOWNWIND.</li>
 *   <li>CIRCUIT_DOWNWIND, abeam the start of the final: CIRCUIT_BASE.</li>
 *   <li>CIRCUIT_BASE, at the start of the final: CIRCUIT_FINAL.</li>
 *   <li>CIRCUIT_FINAL down the glide slope, over the threshold: LANDING.</li>
 *   <li>LANDING rolls out; near B the landing is done.</li>
 * </ul>
 * Any other phase enters DESCEND. Circuit height and offsets are relative to the runway.
 */
final class RecoveryBrain {

    /** Circuit altitude above the runway. */
    private static final double CIRCUIT_HEIGHT = 80.0;
    /** Lateral distance of the downwind leg from the centreline. */
    private static final double CIRCUIT_OFFSET = 80.0;
    /** Length of the final approach. Much longer and the circuit leaves the loaded area. */
    private static final double CIRCUIT_FINAL_DIST = 250.0;
    private static final double CIRCUIT_LEG_ARRIVE_DIST = 30.0;
    private static final double CIRCUIT_BASE_ARRIVE_DIST = 40.0;
    /** The landing roll ends this close to RUNWAY_B. */
    private static final double ROLL_OUT_DIST = 20.0;
    private static final double MIN_TOUCHDOWN_SPEED = 0.5;
    /** On the ground the aircraft steers at a point this far below the runway, which keeps the nose down. */
    private static final double GROUND_AIM_DEPTH = 10;
    /** The final turns into the landing roll this close to the threshold and this low over it. */
    private static final double TOUCHDOWN_DIST = 8.0;
    private static final double TOUCHDOWN_HEIGHT = 6;

    private RecoveryBrain() {
    }

    /**
     * One tick of the landing circuit.
     *
     * @return true once the landing roll has reached RUNWAY_B, or at once on an unusable runway
     */
    static boolean fly(Perception p, Controls c, Autopilot autopilot, Runway runway) {
        if (!runway.isUsable()) {
            return true;
        }
        switch (autopilot.phase) {
            case DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE -> flyPattern(c, autopilot, runway);
            case CIRCUIT_FINAL -> flyFinal(p, c, autopilot, runway);
            case LANDING -> {
                // rolling out: the taxi push after physics moves the aircraft towards B
                c.setThrottle(0.0);
                autopilot.steerTo(runway.bx(), runway.ay() - GROUND_AIM_DEPTH, runway.bz());
                if (c.position().horizontalDistanceTo(runway.bx(), runway.bz()) < ROLL_OUT_DIST) {
                    p.log().debug("[Circuit] {} stopped near RUNWAY_B", p.tag());
                    return true;
                }
            }
            default -> autopilot.enter(FlightPhase.DESCEND);
        }
        return false;
    }

    /** The legs at circuit height: to abeam B, downwind past A, and base onto the final. */
    private static void flyPattern(Controls c, Autopilot autopilot, Runway runway) {
        double circuitY = runway.ay() + CIRCUIT_HEIGHT;
        double perpX = -runway.dirZ();
        double perpZ = runway.dirX();
        double finalStartX = runway.ax() - runway.dirX() * CIRCUIT_FINAL_DIST;
        double finalStartZ = runway.az() - runway.dirZ() * CIRCUIT_FINAL_DIST;
        Point self = c.position();

        switch (autopilot.phase) {
            case DESCEND -> {
                c.setThrottle(circuitThrottle(self, circuitY, 0.35));
                double x = runway.bx() + perpX * CIRCUIT_OFFSET;
                double z = runway.bz() + perpZ * CIRCUIT_OFFSET;
                autopilot.steerTo(x, circuitY, z);
                if (self.horizontalDistanceTo(x, z) < CIRCUIT_LEG_ARRIVE_DIST) {
                    autopilot.enter(FlightPhase.CIRCUIT_DOWNWIND);
                }
            }
            case CIRCUIT_DOWNWIND -> {
                c.setThrottle(circuitThrottle(self, circuitY, 0.4));
                double x = finalStartX + perpX * CIRCUIT_OFFSET;
                double z = finalStartZ + perpZ * CIRCUIT_OFFSET;
                autopilot.steerTo(x, circuitY, z);
                autopilot.phaseTimer++;
                if (self.horizontalDistanceTo(x, z) < CIRCUIT_LEG_ARRIVE_DIST) {
                    autopilot.restart(FlightPhase.CIRCUIT_BASE);
                }
            }
            default -> {
                c.setThrottle(circuitThrottle(self, circuitY, 0.4));
                autopilot.steerTo(finalStartX, circuitY, finalStartZ);
                autopilot.phaseTimer++;
                if (self.horizontalDistanceTo(finalStartX, finalStartZ) < CIRCUIT_BASE_ARRIVE_DIST) {
                    autopilot.restart(FlightPhase.CIRCUIT_FINAL);
                }
            }
        }
    }

    /** Throttle that holds circuit altitude: more power when low, less when high. */
    private static double circuitThrottle(Point self, double circuitY, double minThrottle) {
        return clamp(0.65 + (circuitY - self.y()) * 0.02, minThrottle, 0.9);
    }

    private static void flyFinal(Perception p, Controls c, Autopilot autopilot, Runway runway) {
        Point self = c.position();
        double distBeforeThreshold = -runway.distanceFromStart(self);
        double aimDist = Math.max(0, distBeforeThreshold - DepartureBrain.LOOKAHEAD_DIST);
        double aimX = runway.ax() - runway.dirX() * aimDist;
        double aimZ = runway.az() - runway.dirZ() * aimDist;
        double glideAlt = glideSlopeAltitude(runway, distBeforeThreshold);

        if (self.y() <= runway.ay() + 1) {
            c.setThrottle(0.0);
            autopilot.steerTo(aimX, runway.ay() - GROUND_AIM_DEPTH, aimZ);
        } else {
            // throttle follows the glide-slope error; the hold after physics does the fine vertical control
            double glideError = self.y() - glideAlt;
            c.setThrottle(clamp(0.55 - glideError * 0.02, 0.1, 0.75));
            autopilot.steerTo(aimX, glideAlt, aimZ);
        }
        autopilot.phaseTimer++;

        // only switch to the landing roll at the threshold itself, so an early touchdown keeps rolling to A
        double distToThreshold = self.horizontalDistanceTo(runway.ax(), runway.az());
        boolean overThreshold = distToThreshold < TOUCHDOWN_DIST && self.y() <= runway.ay() + TOUCHDOWN_HEIGHT;
        if (overThreshold || distBeforeThreshold <= 0) {
            autopilot.enter(FlightPhase.LANDING);
            autopilot.landingRollSpeed = Math.max(c.motion().horizontalLength(), MIN_TOUCHDOWN_SPEED);
            p.log().debug("[Circuit] {} touchdown, roll speed {}", p.tag(), autopilot.landingRollSpeed);
        }
    }

    /** Glide-slope altitude at the given distance before the threshold, from circuit height down to touchdown. */
    private static double glideSlopeAltitude(Runway runway, double distBeforeThreshold) {
        double touchdownY = runway.ay() + 1;
        double circuitY = runway.ay() + CIRCUIT_HEIGHT;
        double glide = touchdownY + Math.max(0, distBeforeThreshold) * ((circuitY - touchdownY) / CIRCUIT_FINAL_DIST);
        return clamp(glide, touchdownY, circuitY);
    }

    /**
     * After physics on final: steer vertical speed onto the glide slope (throttle alone sinks too fast
     * and touches down short), and once on the ground roll the aircraft forward to the threshold.
     */
    static void holdFinal(Controls c, Runway runway) {
        if (!runway.isUsable()) {
            return;
        }
        Point self = c.position();
        boolean onGround = self.y() <= runway.ay() + 2;
        if (!onGround) {
            double glideAlt = glideSlopeAltitude(runway, -runway.distanceFromStart(self));
            double glideError = self.y() - glideAlt;
            c.setMotionY(clamp(-glideError * 0.15, -0.4, 0.1));
            return;
        }
        double dx = runway.ax() - self.x();
        double dz = runway.az() - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 1.0) {
            double step = Math.min(0.4, dist * 0.05);
            c.setPosition(self.x() + dx / dist * step, self.y(), self.z() + dz / dist * step);
        }
        c.levelAttitude();
    }
}
