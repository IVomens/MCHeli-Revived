package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Airfield;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;

import javax.annotation.Nullable;

/**
 * Where a mission order's aircraft takes off and comes home: the runway of its base or taxi route,
 * the helipad to land on, the taxi route back to parking and the heading to park with. Lookups only;
 * {@link OrderBrain} decides what to do with them.
 */
final class HomeBase {

    private HomeBase() {
    }

    /** The base's helipad, or the helipad the aircraft took off from even if its base id differs. */
    static @Nullable Marker findHelipad(Perception p, Autopilot autopilot, MissionOrder order) {
        Marker helipad = p.airfield().findByBase(order.baseId(), MarkerType.HELIPAD);
        if (helipad != null) {
            return helipad;
        }
        Marker assigned = p.airfield().findAnyById(autopilot.parkingId, order.baseId());
        return assigned != null && assigned.type() == MarkerType.HELIPAD ? assigned : null;
    }

    /**
     * The order's dedicated arrival route if set, else the route that uses the landing runway, else the
     * departure route, else any route from the parking spot.
     */
    static @Nullable TaxiRoute findArrivalRoute(Perception p, Autopilot autopilot, MissionOrder order,
                                                        Marker parking, @Nullable String runwayStartId) {
        Airfield airfield = p.airfield();
        if (!order.arrivalRouteId().isEmpty()) {
            TaxiRoute route = airfield.findRoute(order.arrivalRouteId());
            if (route != null) {
                return route;
            }
            p.log().warn("[Order] {} arrival route '{}' not found, falling back", p.tag(), order.arrivalRouteId());
        }
        if (runwayStartId != null && !runwayStartId.isEmpty()) {
            TaxiRoute route = airfield.findRouteToRunway(runwayStartId);
            if (route != null) {
                return route;
            }
        }
        if (!autopilot.departureRouteId.isEmpty()) {
            TaxiRoute route = airfield.findRoute(autopilot.departureRouteId);
            if (route != null) {
                return route;
            }
        }
        return airfield.findRouteFromParking(parking.id());
    }

    /**
     * Runway for the order. RUNWAY_A comes from the taxi route (exact), or the base when there is no
     * route; RUNWAY_B from the route's {@code runwayBId}, falling back to the base. Null if either end
     * is missing, in which case takeoff and landing are skipped.
     */
    static @Nullable Runway findRunway(Perception p, Autopilot autopilot, MissionOrder order) {
        Airfield airfield = p.airfield();
        TaxiRoute route = null;
        if (!autopilot.departureRouteId.isEmpty()) {
            route = airfield.findRoute(autopilot.departureRouteId);
        } else if (!autopilot.parkingId.isEmpty()) {
            route = airfield.findRouteFromParking(autopilot.parkingId);
        }
        Marker start = findRunwayStart(p, autopilot, order, route);
        Marker end = findRunwayEnd(p, autopilot, order, route);
        return start != null && end != null ? Runway.of(start, end) : null;
    }

    private static @Nullable Marker findRunwayStart(Perception p, Autopilot autopilot, MissionOrder order,
                                                    @Nullable TaxiRoute route) {
        Airfield airfield = p.airfield();
        if (route != null) {
            Marker start = airfield.findById(MarkerType.RUNWAY_A, route.runwayId());
            if (start == null) {
                p.log().warn("[Order] RUNWAY_A '{}' not found (base '{}', route '{}')", route.runwayId(),
                        order.baseId(), autopilot.departureRouteId);
            }
            return start;
        }
        Marker start = airfield.findByBase(order.baseId(), MarkerType.RUNWAY_A);
        if (start == null) {
            p.log().warn("[Order] no RUNWAY_A for base '{}' (route '{}')", order.baseId(), autopilot.departureRouteId);
        }
        return start;
    }

    private static @Nullable Marker findRunwayEnd(Perception p, Autopilot autopilot, MissionOrder order,
                                                  @Nullable TaxiRoute route) {
        Airfield airfield = p.airfield();
        Marker end = null;
        if (route != null && !route.runwayBId().isEmpty()) {
            end = airfield.findById(MarkerType.RUNWAY_B, route.runwayBId());
            if (end == null) {
                p.log().warn("[Order] RUNWAY_B '{}' not found (base '{}', route '{}'), trying the base",
                        route.runwayBId(), order.baseId(), autopilot.departureRouteId);
            }
        }
        if (end == null) {
            end = airfield.findByBase(order.baseId(), MarkerType.RUNWAY_B);
        }
        if (end == null) {
            p.log().warn("[Order] no RUNWAY_B (base '{}', route '{}'). Set runwayBId on the route or give a RUNWAY_B "
                    + "marker base id '{}'. Takeoff is skipped.", order.baseId(), autopilot.departureRouteId,
                    order.baseId());
        }
        return end;
    }

    /** The parking marker's heading as a yaw to hold, or NaN when it has none. */
    static float parkingHeading(Airfield airfield, String parkingId) {
        if (parkingId.isEmpty()) {
            return Float.NaN;
        }
        for (Marker marker : airfield.markers()) {
            if (marker.type() == MarkerType.PARKING && parkingId.equals(marker.id())) {
                return marker.parkingHeading() >= 0 ? compassToYaw(marker.parkingHeading()) : Float.NaN;
            }
        }
        return Float.NaN;
    }

    /** Compass index (0 north, 1 east, 2 south, 3 west) to Minecraft yaw. */
    private static float compassToYaw(int heading) {
        return switch (heading) {
            case 0 -> 180f;
            case 1 -> -90f;
            case 3 -> 90f;
            default -> 0f;
        };
    }
}
