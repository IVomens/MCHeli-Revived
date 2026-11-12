package com.ivomens.mcheli.core.wingman.brain;

import static com.ivomens.mcheli.core.wingman.brain.Angles.stepTowards;
import static com.ivomens.mcheli.core.wingman.brain.Angles.yawTowards;

/**
 * How an autonomous aircraft is flown in each phase, whatever task chose the phase: the throttle and
 * steering before physics, the taxi push and the rider's heading after it, the landing gear and the
 * VTOL nozzle. The task brains ({@link OrderBrain}, {@link NodeMissionBrain}) only pick the phase,
 * the steering target and, in some phases, the throttle.
 */
final class PhaseControl {

    /** Ground taxi speed in blocks/tick (about 5 m/s). */
    private static final double TAXI_SPEED = 0.25;
    /** Per-tick decay of the landing roll speed down to taxi speed. */
    private static final double LANDING_ROLL_DECAY = 0.965;
    /** Taxiing stops this close to the target. */
    private static final double TAXI_STOP_DIST = 0.5;
    /** Further than this from the target a cruising aircraft flies at full throttle, closer at half. */
    private static final double CRUISE_SLOWDOWN_DIST = 20;

    private PhaseControl() {
    }

    /** Ground movement phases, where the taxi push moves the aircraft. */
    static boolean isTaxiing(FlightPhase phase) {
        return phase == FlightPhase.TAXI_OUT || phase == FlightPhase.TAXI_IN || phase == FlightPhase.LANDING;
    }

    /** Phases whose task brain sets the throttle itself; the phase control only steers. */
    static boolean setsOwnThrottle(FlightPhase phase) {
        return switch (phase) {
            case TAKEOFF_ROLL, CLIMB, DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE, CIRCUIT_FINAL, LANDING,
                 VTOL_TAKEOFF, VTOL_LAND -> true;
            default -> false;
        };
    }

    /** Gear up in flight, down on the ground and from final approach on. */
    static boolean gearUp(FlightPhase phase) {
        return switch (phase) {
            case CLIMB, ENROUTE, ATTACK, LOITER, DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE,
                 TRANSIT_TO, ON_STATION, STRIKE_PASS, RTB, VTOL_TAKEOFF -> true;
            default -> false;
        };
    }

    /**
     * Sends a VTOL plane's nozzle command once per change of the hover mode: the mode switch is not
     * idempotent while the nozzle is moving, so repeating it every tick would never complete.
     */
    static void driveNozzle(Perception p, Controls c, Autopilot autopilot) {
        if (!p.airframe().isVtolPlane()) {
            return;
        }
        boolean wantHover = autopilot.isAutonomous() && autopilot.hoverMode;
        if (wantHover && !autopilot.hoverCommandSent) {
            c.switchNozzle(true);
            autopilot.hoverCommandSent = true;
        } else if (!wantHover && autopilot.hoverCommandSent) {
            c.switchNozzle(false);
            autopilot.hoverCommandSent = false;
        }
    }

    /** Before physics: throttle and steering for the current phase. */
    static void fly(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        showAutopilotToRider(p, c, autopilot);
        FlightPhase phase = autopilot.phase;
        if (phase == FlightPhase.ALIGN) {
            // hold still and turn onto the runway heading at the normal rate limit
            c.setThrottle(0.0);
            c.stopMotion();
            Steering.steer(p, c, a);
            c.levelAttitude();
        } else if (isTaxiing(phase)) {
            prepareTaxiStep(c, autopilot);
        } else if (setsOwnThrottle(phase)) {
            Steering.steer(p, c, a);
            if (phase == FlightPhase.TAKEOFF_ROLL || phase == FlightPhase.CLIMB) {
                c.setThrottle(1.0);
            }
            // in hover any pitch turns into horizontal drift; climb and descend on throttle alone
            if (autopilot.hoverMode) {
                c.setPitch(0.0f);
            }
        } else if (phase == FlightPhase.NONE || phase == FlightPhase.PARKED) {
            // NONE: the task brain starts the order later this tick; moving now would jump the aircraft
            c.setThrottle(0.0);
            c.stopMotion();
        } else {
            Point self = c.position();
            double dx = autopilot.targetX - self.x();
            double dy = autopilot.targetY - self.y();
            double dz = autopilot.targetZ - self.z();
            c.setThrottle(Math.sqrt(dx * dx + dy * dy + dz * dz) > CRUISE_SLOWDOWN_DIST ? 1.0 : 0.5);
            Steering.steer(p, c, a);
        }
    }

    /**
     * A player riding an autonomous aircraft would steer it with their own view: MCHeli copies the
     * rider's yaw into the aircraft at the start of its update. The rider is turned to the autopilot
     * heading first, and their client is told so it stops fighting the autopilot over yaw and throttle.
     */
    private static void showAutopilotToRider(Perception p, Controls c, Autopilot autopilot) {
        if (!p.airframe().playerAboard()) {
            return;
        }
        Point self = c.position();
        double dx = autopilot.targetX - self.x();
        double dz = autopilot.targetZ - self.z();
        // NaN tells the client not to touch the yaw
        float targetYaw = Float.NaN;
        if (dx * dx + dz * dz >= 1.0) {
            targetYaw = yawTowards(dx, dz);
            c.turnPilot(targetYaw);
        }
        p.senses().showAutopilot(targetYaw, (float) c.throttle());
    }

    /**
     * Before physics while taxiing: remember the position and give the aircraft taxi motion towards the
     * target, which drives MCHeli's wheel animation. {@link #pushTaxi} then sets the exact position from
     * the remembered one, so the motion is not applied twice.
     */
    private static void prepareTaxiStep(Controls c, Autopilot autopilot) {
        c.setThrottle(0.0);
        Point self = c.position();
        autopilot.taxiFromX = self.x();
        autopilot.taxiFromZ = self.z();
        double dx = autopilot.targetX - self.x();
        double dz = autopilot.targetZ - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > TAXI_STOP_DIST) {
            c.setHorizontalMotion(dx / dist * TAXI_SPEED, dz / dist * TAXI_SPEED);
            c.setYaw(yawTowards(dx, dz));
        } else {
            c.setHorizontalMotion(0, 0);
        }
        c.setMotionY(0);
        c.levelAttitude();
    }

    /** After physics: the taxi push on the ground, otherwise the rider's heading. */
    static void holdAfterPhysics(Perception p, Controls c, Autopilot autopilot) {
        if (isTaxiing(autopilot.phase)) {
            pushTaxi(c, autopilot);
        } else {
            holdRiderHeading(p, c, autopilot);
        }
    }

    /**
     * Steps from the pre-physics position towards the target at taxi speed (during the landing roll, at
     * the decaying roll speed). Y stays where physics put it, so the aircraft follows the ground.
     */
    private static void pushTaxi(Controls c, Autopilot autopilot) {
        double baseX = autopilot.taxiFromX;
        double baseZ = autopilot.taxiFromZ;
        double dx = autopilot.targetX - baseX;
        double dz = autopilot.targetZ - baseZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < TAXI_STOP_DIST) {
            c.setHorizontalMotion(0, 0);
            return;
        }

        double speed = TAXI_SPEED;
        if (autopilot.phase == FlightPhase.LANDING && autopilot.landingRollSpeed > TAXI_SPEED) {
            autopilot.landingRollSpeed = Math.max(TAXI_SPEED, autopilot.landingRollSpeed * LANDING_ROLL_DECAY);
            speed = autopilot.landingRollSpeed;
        }

        double step = Math.min(speed, dist);
        c.setPosition(baseX + dx / dist * step, c.position().y(), baseZ + dz / dist * step);
        // keep the motion for the wheel animation on the next tick
        c.setHorizontalMotion(dx / dist * speed, dz / dist * speed);
        c.setYaw(yawTowards(dx, dz));
        c.levelAttitude();
    }

    /** A ridden aircraft turns towards its target at the normal rate, and the rider with it. */
    private static void holdRiderHeading(Perception p, Controls c, Autopilot autopilot) {
        if (!p.airframe().playerAboard()) {
            return;
        }
        Point self = c.position();
        double dx = autopilot.targetX - self.x();
        double dz = autopilot.targetZ - self.z();
        if (Math.sqrt(dx * dx + dz * dz) < 1.0) {
            return;
        }
        float targetYaw = yawTowards(dx, dz);
        c.setYaw(stepTowards(c.yaw(), targetYaw, Steering.cruiseYawRate(p.airframe())));
        // MCHeli pushes its yaw back onto the rider after the update; keep the rider on the target too
        c.turnPilot(targetYaw);
    }
}
