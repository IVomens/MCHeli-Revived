package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;

import java.util.ArrayList;
import javax.annotation.Nullable;

import static com.ivomens.mcheli.core.wingman.brain.Angles.yawTowards;

/**
 * Vertical takeoff from and landing on HELIPAD markers, for helicopters and VTOL planes. The phase
 * timer counts through the steps of each:
 * <ul>
 *   <li>VTOL_TAKEOFF: nozzle down (ticks 0-80), idle (80-140), spool up (140-240), then climb
 *       straight up; at cruise altitude, TRANSIT_TO.</li>
 *   <li>VTOL_LAND: approach wing-borne; within 20 blocks of the pad hover for 80 ticks, then descend;
 *       after touchdown on the pad sit still (timer -100 to 0), then TAXI_IN, or park on the pad.</li>
 * </ul>
 * The hover mode tells the phase control whether a VTOL plane's nozzle should be down; helicopters
 * ignore it. The autopilot's parking id holds the helipad id throughout.
 */
final class VtolBrain {

    /** Nozzle rotation takes about 60 ticks; wait a bit longer with the engine off. */
    private static final int NOZZLE_WAIT_END = 80;
    /** Idle at low throttle for a while before spooling up. */
    private static final int IDLE_END = 140;
    /** Throttle ramps up linearly until this tick, then the vertical climb starts. */
    private static final int RAMP_END = 240;
    /** Until this tick of the takeoff the aircraft is held over the pad centre after physics. */
    private static final int PAD_HOLD_TICKS = 180;
    private static final double IDLE_THROTTLE = 0.1;
    private static final double RAMP_THROTTLE = 0.8;
    private static final double CLIMB_THROTTLE = 0.9;
    /** The climb ends this far below cruise altitude. */
    private static final double CLIMB_TOLERANCE = 5;

    /** Distance from the pad where the approach turns into hover and descent. */
    private static final double APPROACH_END_DIST = 20.0;
    private static final double APPROACH_THROTTLE = 0.6;
    /** Ticks to hover and stabilise above the pad before descending. */
    private static final int HOVER_TICKS = 80;
    private static final double HOVER_THROTTLE = 0.5;
    /** Touchdown only counts this close to the pad centre. */
    private static final double TOUCHDOWN_MAX_DIST = 15.0;
    /** Ticks to sit still after touchdown before taxiing; counted up from a negative timer. */
    private static final int POST_LANDING_WAIT = 100;
    /** The horizontal pad-centring only kicks in this close above the pad, so it never looks like air-taxiing. */
    private static final double PAD_CENTERING_HEIGHT = 30.0;
    private static final double MAX_DESCENT_RATE = -0.12;

    /** Distance of the heading reference point from the pad. */
    private static final double HEADING_REF_DIST = 50.0;
    /** Without a HELIPAD_B the landing keeps the nose where it points, through a point this far ahead. */
    private static final double LANDING_HEADING_DIST = 10.0;
    /** Every this many ticks a missing HELIPAD_B is reported again. */
    private static final int WARNING_INTERVAL = 200;

    private VtolBrain() {
    }

    /** Switches into VTOL_TAKEOFF from the given helipad. */
    static void beginTakeoff(Autopilot autopilot, Marker pad, MissionOrder order) {
        autopilot.restart(FlightPhase.VTOL_TAKEOFF);
        autopilot.hoverMode = true;
        // lets the phase control send the nozzle-down command once
        autopilot.hoverCommandSent = false;
        autopilot.parkingId = pad.id();
        autopilot.steerTo(pad.centerX(), CruiseAltitude.above(order.cruiseAlt(), pad.blockY() + 1.0), pad.centerZ());
    }

    /** Wait for the nozzle, idle, ramp the throttle up, then climb straight up to cruise altitude. */
    static void flyTakeoff(Perception p, Controls c, Autopilot autopilot, OrderProgress progress) {
        MissionOrder order = progress.order();
        setHoverMode(p, autopilot, true);

        Marker pad = p.airfield().findAnyById(autopilot.parkingId, order.baseId());
        Point self = c.position();
        double padX = pad != null ? pad.centerX() : self.x();
        double padZ = pad != null ? pad.centerZ() : self.z();
        steerForTakeoffHeading(p, autopilot, progress, pad, padX, padZ);

        autopilot.phaseTimer++;
        int timer = autopilot.phaseTimer;
        if (timer < RAMP_END) {
            double throttle;
            if (timer < NOZZLE_WAIT_END) {
                throttle = 0.0;
            } else if (timer < IDLE_END) {
                throttle = IDLE_THROTTLE;
            } else {
                throttle = IDLE_THROTTLE + (timer - IDLE_END) / (double) (RAMP_END - IDLE_END) * RAMP_THROTTLE;
            }
            c.setThrottle(throttle);
            autopilot.targetY = self.y();
            return;
        }

        c.setThrottle(CLIMB_THROTTLE);
        double cruise = CruiseAltitude.above(order.cruiseAlt(), padTop(p, pad, padX, padZ));
        autopilot.targetY = cruise;
        if (self.y() >= cruise - CLIMB_TOLERANCE) {
            setHoverMode(p, autopilot, false);
            autopilot.enter(FlightPhase.TRANSIT_TO);
            p.log().debug("[VTOL] {} takeoff complete, TRANSIT_TO ({},{})", p.tag(), (int) order.targetX(),
                    (int) order.targetZ());
        }
    }

    /**
     * Picks a fixed point {@link #HEADING_REF_DIST} from the pad as steering target, so the heading is
     * stable even while the aircraft sits exactly above the pad. The direction comes from the base's
     * HELIPAD_B marker if there is one, otherwise from the mission target. The point is mirrored to the
     * other side of the pad because MCHeli's aircraft yaw is offset by 180 degrees from the steering
     * angle, which leaves the nose pointing at the reference.
     */
    private static void steerForTakeoffHeading(Perception p, Autopilot autopilot, OrderProgress progress,
                                               @Nullable Marker pad, double padX, double padZ) {
        Marker padB = headingMarker(p, pad);
        double refX;
        double refZ;
        if (padB != null) {
            refX = padB.centerX();
            refZ = padB.centerZ();
        } else {
            if (pad != null && progress.diagnosticTick % WARNING_INTERVAL == 11) {
                p.log().warn("[VTOL] {} no HELIPAD_B for base '{}', using the mission target as takeoff heading. "
                        + "Place a HELIPAD_B marker with that base id to fix the heading.", p.tag(), pad.baseId());
            }
            refX = progress.order().targetX();
            refZ = progress.order().targetZ();
        }
        if (!steerOppositeOf(autopilot, padX, padZ, refX, refZ)) {
            autopilot.targetX = padX;
            autopilot.targetZ = padZ + HEADING_REF_DIST;
        }
    }

    /** Sets the steering target opposite the reference point; false if the reference is on the pad. */
    private static boolean steerOppositeOf(Autopilot autopilot, double padX, double padZ, double refX, double refZ) {
        double dx = refX - padX;
        double dz = refZ - padZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 1.0) {
            return false;
        }
        autopilot.targetX = padX - dx / len * HEADING_REF_DIST;
        autopilot.targetZ = padZ - dz / len * HEADING_REF_DIST;
        return true;
    }

    private static @Nullable Marker headingMarker(Perception p, @Nullable Marker pad) {
        if (pad == null || pad.baseId().isEmpty()) {
            return null;
        }
        return p.airfield().findByBase(pad.baseId(), MarkerType.HELIPAD_B);
    }

    /** Switches into VTOL_LAND towards the given helipad, approaching in forward flight first. */
    static void beginLanding(Autopilot autopilot, Marker pad) {
        autopilot.parkingId = pad.id();
        autopilot.targetX = pad.centerX();
        autopilot.targetZ = pad.centerZ();
        autopilot.restart(FlightPhase.VTOL_LAND);
        autopilot.hoverMode = false;
    }

    /**
     * Approach the pad in forward flight, hover, descend with a throttle that rises as the ground gets
     * close, then sit still for a moment and hand over to the taxi-in.
     */
    static void flyLanding(Perception p, Controls c, Assignment a, OrderProgress progress) {
        Autopilot autopilot = a.autopilot();
        MissionOrder order = progress.order();
        // always read the pad position from the airfield: the steering target is offset during the
        // descent, so deriving the pad from it would drift a little further every tick
        Marker pad = p.airfield().findAnyById(autopilot.parkingId, order.baseId());
        double padX = pad != null ? pad.centerX() : autopilot.targetX;
        double padZ = pad != null ? pad.centerZ() : autopilot.targetZ;
        double groundY = padTop(p, pad, padX, padZ);

        if (autopilot.phaseTimer < 0) {
            waitAfterTouchdown(p, c, a, padX, padZ, groundY);
            return;
        }

        Point self = c.position();
        double dx = padX - self.x();
        double dz = padZ - self.z();
        double padDist = Math.sqrt(dx * dx + dz * dz);
        if (padDist > APPROACH_END_DIST) {
            // hover mode would stop a VTOL plane from flying forward, so the approach is flown wing-borne
            setHoverMode(p, autopilot, false);
            c.setThrottle(APPROACH_THROTTLE);
            autopilot.steerTo(padX, CruiseAltitude.enroute(p, self, padX, padZ, order.cruiseAlt()), padZ);
            return;
        }

        setHoverMode(p, autopilot, true);
        steerForLandingHeading(p, c, autopilot, pad, padX, padZ);
        autopilot.phaseTimer++;
        if (autopilot.phaseTimer < HOVER_TICKS) {
            c.setThrottle(HOVER_THROTTLE);
            autopilot.targetY = self.y();
            return;
        }
        descend(p, c, autopilot, groundY, padDist);
    }

    /** Where the aircraft stands on the pad: on top of a HELIPAD marker, else on the ground there. */
    private static double padTop(Perception p, @Nullable Marker pad, double padX, double padZ) {
        return pad != null && pad.type() == MarkerType.HELIPAD
                ? pad.blockY() + 1.0
                : p.senses().groundHeight((int) Math.floor(padX), (int) Math.floor(padZ));
    }

    private static void steerForLandingHeading(Perception p, Controls c, Autopilot autopilot, @Nullable Marker pad,
                                               double padX, double padZ) {
        Marker padB = headingMarker(p, pad);
        if (padB != null) {
            steerOppositeOf(autopilot, padX, padZ, padB.centerX(), padB.centerZ());
        } else {
            double facing = Math.toRadians(c.yaw());
            autopilot.targetX = padX - Math.sin(facing) * LANDING_HEADING_DIST;
            autopilot.targetZ = padZ + Math.cos(facing) * LANDING_HEADING_DIST;
        }
    }

    /**
     * Descent throttle rises from 0.45 at 30 blocks to about hover throttle at the ground. Hover throttle
     * differs per aircraft, so the descent-rate clamp after physics backs this up.
     */
    private static void descend(Perception p, Controls c, Autopilot autopilot, double groundY, double padDist) {
        Point self = c.position();
        double height = Math.max(0, self.y() - groundY);
        double throttle = height > 30 ? 0.45 : 0.45 + (30 - height) / 30.0 * 0.12;
        c.setThrottle(throttle);
        autopilot.targetY = groundY;

        boolean touchedDown = c.onGround() || self.y() <= groundY + 1.0;
        if (touchedDown && padDist < TOUCHDOWN_MAX_DIST) {
            autopilot.phaseTimer = -POST_LANDING_WAIT;
            c.setThrottle(0.0);
            p.log().debug("[VTOL] {} touched down at ({},{},{})", p.tag(), (int) self.x(), (int) self.y(),
                    (int) self.z());
        }
    }

    private static void waitAfterTouchdown(Perception p, Controls c, Assignment a, double padX, double padZ,
                                           double groundY) {
        Autopilot autopilot = a.autopilot();
        c.setThrottle(0.0);
        setHoverMode(p, autopilot, false);
        c.stopMotion();
        // the steering runs even now, so pin the target to the pad
        autopilot.steerTo(padX, groundY, padZ);
        autopilot.phaseTimer++;
        if (autopilot.phaseTimer >= 0) {
            finishLanding(p, c, a);
        }
    }

    /**
     * Taxis back to the parking spot of the route whose runway end is this helipad, or parks on the pad
     * if there is none.
     */
    private static void finishLanding(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        c.setThrottle(0.0);
        setHoverMode(p, autopilot, false);

        String padId = autopilot.parkingId;
        TaxiRoute route = padId.isEmpty() ? null : p.airfield().findRouteToRunway(padId);
        if (route == null) {
            OrderBrain.completeMission(p, autopilot);
            return;
        }
        autopilot.parkingId = route.parkingId();
        autopilot.taxiQueue = new ArrayList<>(route.fullArrival());
        autopilot.taxiIndex = 0;
        // the taxi push measures from these; stale values from the departure would teleport the aircraft
        Point self = c.position();
        autopilot.taxiFromX = self.x();
        autopilot.taxiFromZ = self.z();
        autopilot.enter(FlightPhase.TAXI_IN);
        p.log().debug("[VTOL] {} landed on '{}', TAXI_IN via route '{}'", p.tag(), padId, route.routeId());
    }

    private static void setHoverMode(Perception p, Autopilot autopilot, boolean hover) {
        if (autopilot.hoverMode == hover) {
            return;
        }
        autopilot.hoverMode = hover;
        p.log().debug("[VTOL] {} hover mode {}", p.tag(), hover ? "on" : "off");
    }

    /**
     * After physics during VTOL operations. While waiting on the pad before takeoff, and while hovering
     * down onto it, the aircraft is nudged towards the pad centre through its motion (which keeps the
     * wheel animation working). Before takeoff vertical motion is cancelled so hover physics cannot lift
     * it early; during the descent the sink rate is capped.
     *
     * @return true if the phase was handled and no other correction applies
     */
    static boolean holdAfterPhysics(Perception p, Controls c, Autopilot autopilot) {
        boolean takeoffWait = autopilot.phase == FlightPhase.VTOL_TAKEOFF && autopilot.phaseTimer < PAD_HOLD_TICKS;
        boolean hoverDescent = autopilot.phase == FlightPhase.VTOL_LAND && autopilot.hoverMode;
        if (takeoffWait || hoverDescent) {
            centreOverPad(p, c, autopilot, takeoffWait);
        }

        if (autopilot.phase == FlightPhase.VTOL_LAND && (autopilot.hoverMode || autopilot.phaseTimer < 0)) {
            // physics pitches the nose down from the velocity vector, which makes the aircraft bounce on
            // the pad or descend nose-first
            c.levelAttitude();
            return true;
        }
        if (takeoffWait) {
            // ground torque would otherwise turn the aircraft while it waits on the pad
            Point self = c.position();
            double dx = autopilot.targetX - self.x();
            double dz = autopilot.targetZ - self.z();
            if (Math.sqrt(dx * dx + dz * dz) >= 1.0) {
                c.setYaw(yawTowards(dx, dz));
            }
            return true;
        }
        return false;
    }

    private static void centreOverPad(Perception p, Controls c, Autopilot autopilot, boolean takeoffWait) {
        OrderProgress progress = autopilot.order();
        String baseId = progress != null ? progress.order().baseId() : "";
        Marker pad = p.airfield().findAnyById(autopilot.parkingId, baseId);
        if (pad == null) {
            c.setHorizontalMotion(0, 0);
            return;
        }
        Point self = c.position();
        double dx = pad.centerX() - self.x();
        double dz = pad.centerZ() - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);

        boolean closeAbovePad = true;
        if (!takeoffWait) {
            double padY = pad.type() == MarkerType.HELIPAD
                    ? pad.blockY() + 1.0
                    : p.senses().groundHeight(pad.blockX(), pad.blockZ());
            closeAbovePad = self.y() <= padY + PAD_CENTERING_HEIGHT;
        }
        if (closeAbovePad) {
            if (dist > 0.2) {
                // proportional to the distance, at most 0.5 blocks/tick, so it settles smoothly
                double speed = Math.min(0.5, dist * 0.05);
                c.setHorizontalMotion(dx / dist * speed, dz / dist * speed);
            } else {
                c.setHorizontalMotion(0, 0);
            }
        }

        if (takeoffWait) {
            c.setMotionY(0);
        } else {
            c.setMotionY(Math.max(c.motion().y(), MAX_DESCENT_RATE));
        }
    }
}
