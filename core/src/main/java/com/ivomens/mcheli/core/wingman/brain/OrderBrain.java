package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Airfield;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.core.wingman.mission.MissionType;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Flies a {@link MissionOrder}, phase by phase:
 * <ul>
 *   <li>NONE or PARKED: with a parking spot and a route, TAXI_OUT; from a helipad, VTOL_TAKEOFF
 *       ({@link VtolBrain}); otherwise TRANSIT_TO, an airborne start.</li>
 *   <li>TAXI_OUT along the route; at its end a helipad starts VTOL_TAKEOFF, a runway threshold
 *       ALIGN ({@link DepartureBrain} up to CLIMB), and at cruise altitude TRANSIT_TO.</li>
 *   <li>TRANSIT_TO: within 30 blocks of the target, ON_STATION.</li>
 *   <li>ON_STATION orbits and attacks (or only watches, for recon); strike orders alternate with
 *       STRIKE_PASS. The time limit, bingo fuel, the end of a ferry wait or of the strike passes
 *       start RTB.</li>
 *   <li>RTB: to a helipad, VTOL_LAND; within 200 blocks of RUNWAY_B, DESCEND
 *       ({@link RecoveryBrain} down to LANDING), then TAXI_IN to a free parking spot.</li>
 *   <li>TAXI_IN: at the end of the route, PARKED, and the order is done.</li>
 * </ul>
 * Every transition is in this class or in the brain it hands over to. A plane without a runway to
 * come back to, or a helicopter sent down a runway route, stops the order and parks.
 */
final class OrderBrain {

    /** Horizontal distance at which the target area counts as reached. */
    private static final double ARRIVE_DIST = 30.0;
    /** Taxi waypoints are reached when the aircraft is (almost) on top of them; the taxi push stops at 0.5. */
    private static final double TAXI_WAYPOINT_DIST = 1.0;
    /** The landing circuit starts this far from RUNWAY_B. */
    private static final double CIRCUIT_ENTRY_DIST = 200.0;
    private static final double BINGO_FUEL_RATIO = 0.20;
    /** Ticks a ferry flight waits at the destination before heading home. */
    private static final int FERRY_WAIT_TICKS = 100;
    private static final int STRIKE_PASS_TICKS = 300;
    /** Radians per tick around the target between strike passes. */
    private static final double STRIKE_ORBIT_RATE = 0.012;
    private static final double STRIKE_MAX_RADIUS = 100.0;
    private static final double STRIKE_RADIUS_SHARE = 0.3;
    /** Radians per tick: about 900 ticks per orbit. */
    private static final double ORBIT_RATE = 0.007;
    private static final int RECON_COUNT_INTERVAL = 100;

    private OrderBrain() {
    }

    /** One tick of the order. */
    static void fly(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        OrderProgress progress = autopilot.order();
        progress.diagnosticTick++;

        if ((autopilot.phase == FlightPhase.ON_STATION || autopilot.phase == FlightPhase.STRIKE_PASS)
                && checkReturnToBase(p, a, progress)) {
            return;
        }

        switch (autopilot.phase) {
            case NONE, PARKED -> start(p, autopilot, progress);
            case TAXI_OUT -> taxiOut(p, c, autopilot, progress);
            case ALIGN, TAKEOFF_ROLL, CLIMB -> takeoff(p, c, autopilot, progress);
            case TRANSIT_TO -> transit(p, c, autopilot, progress);
            case ON_STATION -> onStation(p, c, a, progress);
            case STRIKE_PASS -> strikePass(p, c, a, progress);
            case RTB -> returnToBase(p, c, a, progress);
            case VTOL_TAKEOFF -> VtolBrain.flyTakeoff(p, c, autopilot, progress);
            case VTOL_LAND -> VtolBrain.flyLanding(p, c, a, progress);
            case DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE, CIRCUIT_FINAL, LANDING -> land(p, c, a, progress);
            case TAXI_IN -> taxiIn(p, c, autopilot, progress);
            default -> {
            }
        }
    }

    /** On station the mission ends when the time limit runs out or fuel gets low. */
    private static boolean checkReturnToBase(Perception p, Assignment a, OrderProgress progress) {
        progress.timer++;
        MissionOrder order = progress.order();
        if (order.timeLimitSeconds() > 0 && progress.timer >= order.timeLimitTicks()) {
            returnToBase(p, a, "time limit");
            return true;
        }
        if (isBingoFuel(p.airframe())) {
            returnToBase(p, a, "bingo fuel");
            return true;
        }
        return false;
    }

    /** Starts the order: from a helipad or parking spot if one is assigned, otherwise airborne. */
    private static void start(Perception p, Autopilot autopilot, OrderProgress progress) {
        MissionOrder order = progress.order();
        if (autopilot.parkingId.isEmpty()) {
            p.log().debug("[Order] {} no parking assigned, airborne start", p.tag());
        } else if (startFromParking(p, autopilot, order)) {
            return;
        }
        autopilot.enter(FlightPhase.TRANSIT_TO);
        if (order.targetX() == 0 && order.targetZ() == 0) {
            p.log().warn("[Order] {} target is (0,0); set Target X/Z in the planner. "
                    + "The aircraft will fly to the world origin.", p.tag());
        }
        p.log().info("[Order] {} TRANSIT_TO ({},{}) base='{}' timeLimit={}s", p.tag(), (int) order.targetX(),
                (int) order.targetZ(), order.baseId(), order.timeLimitSeconds());
    }

    /** @return true if the aircraft starts from the ground, false to fall back to an airborne start */
    private static boolean startFromParking(Perception p, Autopilot autopilot, MissionOrder order) {
        Airfield airfield = p.airfield();
        Marker spot = airfield.findAnyById(autopilot.parkingId, order.baseId());
        if (spot != null && spot.type() == MarkerType.HELIPAD) {
            if (p.airframe().canUseHelipad()) {
                VtolBrain.beginTakeoff(autopilot, spot, order);
                p.log().info("[Order] {} VTOL_TAKEOFF from helipad '{}'", p.tag(), spot.id());
                return true;
            }
            p.log().warn("[Order] {} is assigned helipad '{}' but cannot use helipads, airborne start",
                    p.tag(), autopilot.parkingId);
            return false;
        }

        // the departure route id is exact; several routes may share a parking spot
        TaxiRoute route = !autopilot.departureRouteId.isEmpty()
                ? airfield.findRoute(autopilot.departureRouteId)
                : airfield.findRouteFromParking(autopilot.parkingId);
        if (route == null) {
            p.log().warn("[Order] {} no taxi route for parking '{}', airborne start", p.tag(), autopilot.parkingId);
            return false;
        }
        autopilot.taxiQueue = new ArrayList<>(route.fullDeparture());
        // the queue starts with the parking spot itself; skip it: the aircraft may not be on it (just
        // spawned), and sending every aircraft back to the same spot makes them pile up
        autopilot.taxiIndex = autopilot.taxiQueue.size() > 1 ? 1 : 0;
        autopilot.enter(FlightPhase.TAXI_OUT);
        p.log().info("[Order] {} TAXI_OUT from '{}' via route '{}'", p.tag(), autopilot.parkingId, route.routeId());
        return true;
    }

    private static void taxiOut(Perception p, Controls c, Autopilot autopilot, OrderProgress progress) {
        if (autopilot.taxiIndex < autopilot.taxiQueue.size()) {
            followTaxiQueue(p, c, autopilot, progress.order());
            return;
        }
        // end of the queue: a helipad means vertical takeoff, anything else is a runway threshold
        List<String> queue = autopilot.taxiQueue;
        String lastId = queue.isEmpty() ? "" : queue.get(queue.size() - 1);
        Marker last = p.airfield().findAnyById(lastId, progress.order().baseId());
        if (last != null && last.type() == MarkerType.HELIPAD && p.airframe().canUseHelipad()) {
            VtolBrain.beginTakeoff(autopilot, last, progress.order());
            p.log().debug("[Order] {} taxied to helipad '{}', VTOL_TAKEOFF", p.tag(), lastId);
            return;
        }
        // VTOL planes can take off conventionally; helicopters cannot
        if (p.airframe().isHelicopter()) {
            p.log().error("[Order] {} is a helicopter on runway route '{}'; assign a helipad route instead. "
                    + "Mission aborted.", p.tag(), autopilot.departureRouteId);
            autopilot.enter(FlightPhase.PARKED);
            autopilot.endOrder();
            return;
        }
        autopilot.restart(FlightPhase.ALIGN);
    }

    private static void taxiIn(Perception p, Controls c, Autopilot autopilot, OrderProgress progress) {
        if (autopilot.taxiIndex < autopilot.taxiQueue.size()) {
            followTaxiQueue(p, c, autopilot, progress.order());
            return;
        }
        c.setThrottle(0.0);
        completeMission(p, autopilot);
    }

    /** Steers to the current taxi waypoint and moves on when it is reached. */
    private static void followTaxiQueue(Perception p, Controls c, Autopilot autopilot, MissionOrder order) {
        String waypointId = autopilot.taxiQueue.get(autopilot.taxiIndex);
        Marker waypoint = p.airfield().findAnyById(waypointId, order.baseId());
        if (waypoint == null) {
            p.log().warn("[Order] {} taxi waypoint {}/{} '{}' not found, skipping", p.tag(), autopilot.taxiIndex + 1,
                    autopilot.taxiQueue.size(), waypointId);
            autopilot.taxiIndex++;
            return;
        }
        Point self = c.position();
        double x = waypoint.centerX();
        double z = waypoint.centerZ();
        autopilot.steerTo(x, self.y(), z);
        // the taxi push after physics does the actual ground movement
        c.setThrottle(0.0);
        if (self.horizontalDistanceTo(x, z) < TAXI_WAYPOINT_DIST) {
            autopilot.taxiIndex++;
        }
    }

    private static void takeoff(Perception p, Controls c, Autopilot autopilot, OrderProgress progress) {
        Runway runway = HomeBase.findRunway(p, autopilot, progress.order());
        if (runway == null) {
            autopilot.enter(FlightPhase.TRANSIT_TO);
            return;
        }
        MissionOrder order = progress.order();
        if (DepartureBrain.fly(p, c, autopilot, runway, CruiseAltitude.above(order.cruiseAlt(), runway.ay()))) {
            autopilot.enter(FlightPhase.TRANSIT_TO);
            p.log().debug("[Order] {} cruise altitude reached, TRANSIT_TO ({},{})", p.tag(), (int) order.targetX(),
                    (int) order.targetZ());
        }
    }

    private static void land(Perception p, Controls c, Assignment a, OrderProgress progress) {
        Runway runway = HomeBase.findRunway(p, a.autopilot(), progress.order());
        if (runway == null) {
            finishLanding(p, a, progress, null, null);
        } else if (RecoveryBrain.fly(p, c, a.autopilot(), runway)) {
            finishLanding(p, a, progress, runway.startId(), runway.endId());
        }
    }

    private static void transit(Perception p, Controls c, Autopilot autopilot, OrderProgress progress) {
        MissionOrder order = progress.order();
        c.setThrottle(1.0);
        autopilot.steerTo(order.targetX(), cruise(p, c, order, order.targetX(), order.targetZ()), order.targetZ());
        if (c.position().horizontalDistanceTo(order.targetX(), order.targetZ()) < ARRIVE_DIST) {
            autopilot.enter(FlightPhase.ON_STATION);
            progress.timer = 0;
            p.log().debug("[Order] {} on station, types={}", p.tag(), order.missionTypes());
        }
    }

    private static void onStation(Perception p, Controls c, Assignment a, OrderProgress progress) {
        Autopilot autopilot = a.autopilot();
        MissionOrder order = progress.order();
        if (order.hasType(MissionType.STRIKE) && progress.strikePassesRemaining > 0) {
            autopilot.restart(FlightPhase.STRIKE_PASS);
            return;
        }
        if (order.hasType(MissionType.FERRY)) {
            if (progress.timer >= FERRY_WAIT_TICKS) {
                returnToBase(p, a, "ferry complete");
            }
            return;
        }
        // orbit the target area and engage anything hostile; a pure recon flight only watches
        boolean reconOnly = order.hasType(MissionType.RECON) && !order.hasType(MissionType.CAP)
                && !order.hasType(MissionType.CAS) && !order.hasType(MissionType.ESCORT);
        orbit(p, c, autopilot, order);
        a.attack().setMode(reconOnly ? AttackMode.NONE : AttackMode.AUTO);
        if (order.hasType(MissionType.RECON) && progress.timer % RECON_COUNT_INTERVAL == 0) {
            progress.reconMobCount = p.senses().hostilesAround(order.targetX(), order.targetZ(), order.orbitRadius());
        }
    }

    /** A strike pass runs through the target from the far side of the orbit, at full throttle. */
    private static void strikePass(Perception p, Controls c, Assignment a, OrderProgress progress) {
        Autopilot autopilot = a.autopilot();
        MissionOrder order = progress.order();
        autopilot.phaseTimer++;
        a.attack().setMode(AttackMode.AUTO);
        c.setThrottle(1.0);
        double angle = autopilot.orbitAngle + Math.PI;
        double radius = Math.min(order.orbitRadius() * STRIKE_RADIUS_SHARE, STRIKE_MAX_RADIUS);
        double x = order.targetX() + Math.cos(angle) * radius;
        double z = order.targetZ() + Math.sin(angle) * radius;
        autopilot.steerTo(x, cruise(p, c, order, x, z), z);
        autopilot.orbitAngle += STRIKE_ORBIT_RATE;

        if (autopilot.phaseTimer < STRIKE_PASS_TICKS) {
            return;
        }
        progress.strikePassesRemaining--;
        autopilot.phaseTimer = 0;
        if (progress.strikePassesRemaining <= 0) {
            returnToBase(p, a, "strike complete");
        } else {
            autopilot.enter(FlightPhase.ON_STATION);
        }
    }

    /**
     * Heads home and picks the landing: a helipad for aircraft landing vertically, otherwise the landing
     * circuit of RUNWAY_B. A VTOL-capable aircraft with neither lands where it is; a plane without a
     * runway stops the order.
     */
    private static void returnToBase(Perception p, Controls c, Assignment a, OrderProgress progress) {
        Autopilot autopilot = a.autopilot();
        MissionOrder order = progress.order();
        c.setThrottle(1.0);
        a.attack().setMode(AttackMode.NONE);

        // helicopters always land vertically; VTOL planes only when the order asked for short takeoff and landing
        boolean landsVertically = p.airframe().isHelicopter() || (p.airframe().isVtolPlane() && order.useVstol());
        if (landsVertically) {
            Marker helipad = HomeBase.findHelipad(p, autopilot, order);
            if (helipad != null) {
                VtolBrain.beginLanding(autopilot, helipad);
                p.log().debug("[Order] {} RTB to helipad '{}'", p.tag(), helipad.id());
                return;
            }
        }

        Marker runwayEnd = p.airfield().findByBase(order.baseId(), MarkerType.RUNWAY_B);
        if (runwayEnd == null) {
            runwayEnd = p.airfield().findById(MarkerType.RUNWAY_B, order.baseId());
        }
        if (runwayEnd == null) {
            abandonLanding(p, c, autopilot, order, landsVertically);
            return;
        }

        double x = runwayEnd.centerX();
        double z = runwayEnd.centerZ();
        autopilot.steerTo(x, cruise(p, c, order, x, z), z);
        if (c.position().horizontalDistanceTo(x, z) < CIRCUIT_ENTRY_DIST) {
            autopilot.restart(FlightPhase.DESCEND);
            p.log().debug("[Order] {} entering the landing circuit at base '{}'", p.tag(), order.baseId());
        }
    }

    /** No runway to come back to: land in place if the aircraft can, else stop the order. */
    private static void abandonLanding(Perception p, Controls c, Autopilot autopilot, MissionOrder order,
                                       boolean landsVertically) {
        if (landsVertically) {
            Point self = c.position();
            autopilot.targetX = self.x();
            autopilot.targetZ = self.z();
            autopilot.enter(FlightPhase.VTOL_LAND);
            autopilot.hoverMode = true;
            p.log().warn("[Order] {} RTB: no runway or helipad for base '{}', landing in place", p.tag(),
                    order.baseId());
        } else {
            autopilot.endOrder();
            autopilot.enter(FlightPhase.PARKED);
            p.log().warn("[Order] {} RTB: no RUNWAY_B for base '{}', mission aborted", p.tag(), order.baseId());
        }
    }

    /**
     * After a runway landing: find a free parking spot and a taxi route to it and continue with TAXI_IN,
     * or park on the runway if there is none.
     *
     * @param runwayStartId the RUNWAY_A used for the landing, or null if unknown; it identifies the route
     *                      when several routes share a parking spot
     * @param runwayEndId   the RUNWAY_B where the landing roll stopped, or null; the taxi-in then starts there
     */
    private static void finishLanding(Perception p, Assignment a, OrderProgress progress,
                                      @Nullable String runwayStartId, @Nullable String runwayEndId) {
        Autopilot autopilot = a.autopilot();
        MissionOrder order = progress.order();
        a.attack().setMode(AttackMode.NONE);

        Airfield airfield = p.airfield();
        Marker parking = airfield.findFreeParking(order.baseId());
        if (parking == null && !autopilot.parkingId.isEmpty()) {
            parking = airfield.findById(MarkerType.PARKING, autopilot.parkingId, order.baseId());
            if (parking == null) {
                parking = airfield.findById(MarkerType.HELIPAD, autopilot.parkingId, order.baseId());
            }
        }

        TaxiRoute route = parking != null
                ? HomeBase.findArrivalRoute(p, autopilot, order, parking, runwayStartId) : null;
        if (route == null) {
            completeMission(p, autopilot);
            return;
        }

        autopilot.parkingId = parking.id();
        List<String> arrival = route.fullArrival();
        if (runwayEndId != null && !runwayEndId.isEmpty()) {
            // the roll stopped at B: replace the arrival entry point with B and keep the rest of the path
            List<String> queue = new ArrayList<>(arrival.size());
            queue.add(runwayEndId);
            queue.addAll(arrival.subList(1, arrival.size()));
            autopilot.taxiQueue = queue;
        } else {
            autopilot.taxiQueue = new ArrayList<>(arrival);
        }
        autopilot.taxiIndex = 0;
        autopilot.enter(FlightPhase.TAXI_IN);
        p.log().debug("[Order] {} TAXI_IN to '{}' via route '{}'", p.tag(), parking.id(), route.routeId());
    }

    /** Ends the order where the aircraft is now. */
    static void completeMission(Perception p, Autopilot autopilot) {
        autopilot.enter(FlightPhase.PARKED);
        autopilot.endOrder();
        autopilot.parkingHeadingYaw = HomeBase.parkingHeading(p.airfield(), autopilot.parkingId);
        p.log().info("[Order] {} mission complete, parked at '{}'", p.tag(), autopilot.parkingId);
    }

    private static void orbit(Perception p, Controls c, Autopilot autopilot, MissionOrder order) {
        c.setThrottle(1.0);
        Point next = autopilot.orbit(order.targetX(), order.cruiseAlt(), order.targetZ(), order.orbitRadius(),
                ORBIT_RATE);
        autopilot.steerTo(next.x(), cruise(p, c, order, next.x(), next.z()), next.z());
    }

    /** The order's cruise altitude on the way to (x, z), clear of the ground. */
    private static double cruise(Perception p, Controls c, MissionOrder order, double x, double z) {
        return CruiseAltitude.enroute(p, c.position(), x, z, order.cruiseAlt());
    }

    /** Stops attacking and heads home, telling nearby players what a recon flight counted. */
    private static void returnToBase(Perception p, Assignment a, String reason) {
        Autopilot autopilot = a.autopilot();
        OrderProgress progress = autopilot.order();
        progress.rtbReason = reason;
        autopilot.enter(FlightPhase.RTB);
        a.attack().setMode(AttackMode.NONE);
        p.log().info("[Order] {} returning to base: {}", p.tag(), reason);
        if (progress.order().hasType(MissionType.RECON)) {
            p.senses().reportRecon(progress.reconMobCount, reason);
        }
    }

    private static boolean isBingoFuel(Airframe airframe) {
        // aircraft without a fuel budget never run dry
        if (airframe.fuelCapacity() <= 0 || airframe.fuel() < 0) {
            return false;
        }
        return (double) airframe.fuel() / airframe.fuelCapacity() < BINGO_FUEL_RATIO;
    }
}
