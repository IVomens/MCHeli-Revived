package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;

import javax.annotation.Nullable;

/**
 * Flies a node mission one node at a time, in order, and parks when the list is done. Each node sets
 * the phase while it runs:
 * <pre>
 *   FLY_TO  ENROUTE ──within 30──▶ next
 *   TAKEOFF TAXI_OUT … CLIMB (DepartureBrain) ──cruise altitude──▶ next
 *   LAND    DESCEND … LANDING (RecoveryBrain) ──near RUNWAY_B──▶ next
 *   ATTACK  ATTACK ──no hostile left in the radius──▶ next
 *   LOITER  LOITER ──its duration──▶ next
 *   PARK    TAXI_IN ──within 8 of the spot──▶ PARKED, next
 *   (done)  PARKED, the mission ends
 * </pre>
 * A node whose markers are missing is skipped.
 */
final class NodeMissionBrain {

    /** Horizontal distance at which a FLY_TO node counts as reached; altitude is ignored. */
    private static final double ARRIVE_DIST = 30.0;
    private static final double PARK_ARRIVE_DIST = 8.0;
    /**
     * Minimum altitude while flying between nodes, so ground-level waypoints do not cause a dive; over
     * high ground {@link CruiseAltitude} keeps clear of it.
     */
    private static final double CRUISE_ALT = 80.0;
    /** The hostile check of an ATTACK node is an area query; it runs every this many ticks. */
    private static final int ATTACK_SCAN_INTERVAL = 10;
    private static final double LOITER_RADIUS = 20.0;
    private static final double LOITER_PERIOD_TICKS = 100.0;

    private NodeMissionBrain() {
    }

    /** Runs the current node, or parks the aircraft when the list is done. */
    static void fly(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        MissionNode node = autopilot.mission().current();
        if (node == null) {
            autopilot.enter(FlightPhase.PARKED);
            autopilot.endMission();
            p.log().info("[Auto] {} mission complete", p.tag());
            return;
        }
        switch (node.type) {
            case FLY_TO -> flyTo(p, c, autopilot, node);
            case TAKEOFF -> takeoff(p, c, autopilot, node);
            case LAND -> land(p, c, autopilot, node);
            case ATTACK -> attack(p, a, node);
            case LOITER -> loiter(c, autopilot, node);
            case PARK -> park(p, c, autopilot, node);
        }
    }

    /** Runway of a TAKEOFF or LAND node, or null if either end is missing. */
    static @Nullable Runway findRunway(Perception p, MissionNode node) {
        Marker start = p.airfield().resolveMarker(node.runwayId, MarkerType.RUNWAY_A);
        Marker end = p.airfield().resolveMarker(node.runwayId, MarkerType.RUNWAY_B);
        return start != null && end != null ? Runway.of(start, end) : null;
    }

    /** Runway of the current node if it is a takeoff, for the holds after physics. */
    static @Nullable Runway takeoffRunway(Perception p, Autopilot autopilot) {
        MissionNode node = autopilot.mission() != null ? autopilot.mission().current() : null;
        if (node == null || node.type != MissionNode.Type.TAKEOFF) {
            return null;
        }
        return findRunway(p, node);
    }

    private static void flyTo(Perception p, Controls c, Autopilot autopilot, MissionNode node) {
        autopilot.enter(FlightPhase.ENROUTE);
        c.setThrottle(1.0);
        double cruise = CruiseAltitude.enroute(p, c.position(), node.x, node.z, Math.max(node.y, CRUISE_ALT));
        autopilot.steerTo(node.x, cruise, node.z);
        if (c.position().horizontalDistanceTo(node.x, node.z) < ARRIVE_DIST) {
            autopilot.advanceMission();
        }
    }

    private static void takeoff(Perception p, Controls c, Autopilot autopilot, MissionNode node) {
        Runway runway = findRunway(p, node);
        if (runway == null) {
            p.log().warn("[Auto] {} TAKEOFF: runway '{}' is missing an end, skipping", p.tag(), node.runwayId);
            autopilot.advanceMission();
            return;
        }
        if (!runway.isUsable()) {
            autopilot.advanceMission();
            return;
        }
        if (DepartureBrain.flyFromThreshold(p, c, autopilot, runway, CruiseAltitude.above(CRUISE_ALT, runway.ay()))) {
            autopilot.advanceMission();
        }
    }

    private static void land(Perception p, Controls c, Autopilot autopilot, MissionNode node) {
        Runway runway = findRunway(p, node);
        if (runway == null) {
            p.log().warn("[Auto] {} LAND: runway '{}' is missing an end, skipping", p.tag(), node.runwayId);
            autopilot.advanceMission();
            return;
        }
        if (RecoveryBrain.fly(p, c, autopilot, runway)) {
            autopilot.advanceMission();
        }
    }

    /** Attacks while hostiles remain within the radius, then moves on. */
    private static void attack(Perception p, Assignment a, MissionNode node) {
        Autopilot autopilot = a.autopilot();
        autopilot.enter(FlightPhase.ATTACK);
        if (autopilot.phaseTimer++ % ATTACK_SCAN_INTERVAL != 0) {
            return;
        }
        boolean hasTarget = p.senses().hostileWithin(node.attackRadius);
        a.attack().setMode(hasTarget ? AttackMode.AUTO : AttackMode.NONE);
        if (!hasTarget) {
            autopilot.advanceMission();
        }
    }

    /** Circles a point that rotates around the aircraft. */
    private static void loiter(Controls c, Autopilot autopilot, MissionNode node) {
        autopilot.enter(FlightPhase.LOITER);
        autopilot.phaseTimer++;
        double angle = autopilot.phaseTimer * Math.PI / LOITER_PERIOD_TICKS;
        Point self = c.position();
        autopilot.steerTo(self.x() + Math.cos(angle) * LOITER_RADIUS, self.y(),
                self.z() + Math.sin(angle) * LOITER_RADIUS);
        if (autopilot.phaseTimer >= node.durationTicks) {
            autopilot.advanceMission();
        }
    }

    private static void park(Perception p, Controls c, Autopilot autopilot, MissionNode node) {
        autopilot.enter(FlightPhase.TAXI_IN);
        Marker parking = p.airfield().resolveMarker(node.parkingId, MarkerType.PARKING);
        if (parking == null) {
            autopilot.advanceMission();
            return;
        }
        Point self = c.position();
        double x = parking.centerX();
        double z = parking.centerZ();
        autopilot.steerTo(x, self.y(), z);
        c.setThrottle(0.0);
        if (self.horizontalDistanceTo(x, z) < PARK_ARRIVE_DIST) {
            autopilot.enter(FlightPhase.PARKED);
            autopilot.advanceMission();
        }
    }
}
