package com.ivomens.mcheli.core.wingman.airfield;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A taxi route: parking spot, then an ordered list of waypoints, then the runway threshold.
 *
 * <p>{@code runwayId} is normally the RUNWAY_A marker where the takeoff roll starts; an arrival-only
 * route that starts at the far end after landing may point it at RUNWAY_B instead. {@code runwayBId}
 * is the end of the takeoff roll; empty means "find it automatically".
 *
 * @param waypointIds        WAYPOINT marker ids in departure order
 * @param arrivalWaypointIds waypoints used when taxiing in after landing; empty means the departure
 *                           waypoints reversed
 * @param parkingHeading     heading to hold once parked: {@link #HEADING_ANY}, or 0 north, 1 east,
 *                           2 south, 3 west
 * @param arrivalRunwayId    first marker of the arrival path (for example the B end of the runway or a
 *                           helipad on the other side); empty means {@code runwayId}
 * @param owner              player who saved the route; null for routes saved before owners were recorded
 */
public record TaxiRoute(String routeId, String baseId, String parkingId, String runwayId, String runwayBId,
                        List<String> waypointIds, List<String> arrivalWaypointIds, int parkingHeading,
                        String arrivalRunwayId, @Nullable UUID owner) {

    /** {@link #parkingHeading} value meaning "any heading". */
    public static final int HEADING_ANY = -1;

    public TaxiRoute {
        runwayBId = runwayBId != null ? runwayBId : "";
        waypointIds = List.copyOf(waypointIds);
        arrivalWaypointIds = arrivalWaypointIds != null ? List.copyOf(arrivalWaypointIds) : List.of();
        arrivalRunwayId = arrivalRunwayId != null ? arrivalRunwayId : "";
    }

    /** Departure path: parking, waypoints, runway. */
    public List<String> fullDeparture() {
        List<String> all = new ArrayList<>(this.waypointIds.size() + 2);
        all.add(this.parkingId);
        all.addAll(this.waypointIds);
        all.add(this.runwayId);
        return all;
    }

    /** Arrival path: arrival entry, arrival waypoints (or departure waypoints reversed), parking. */
    public List<String> fullArrival() {
        List<String> all = new ArrayList<>();
        all.add(effectiveArrivalEntry());
        if (!this.arrivalWaypointIds.isEmpty()) {
            all.addAll(this.arrivalWaypointIds);
        } else {
            List<String> reversed = new ArrayList<>(this.waypointIds);
            Collections.reverse(reversed);
            all.addAll(reversed);
        }
        all.add(this.parkingId);
        return all;
    }

    /** The marker the arrival path starts at. */
    public String effectiveArrivalEntry() {
        return this.arrivalRunwayId.isEmpty() ? this.runwayId : this.arrivalRunwayId;
    }
}
