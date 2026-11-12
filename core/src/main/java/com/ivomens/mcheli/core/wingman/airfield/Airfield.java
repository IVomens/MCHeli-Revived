package com.ivomens.mcheli.core.wingman.airfield;

import java.util.List;
import javax.annotation.Nullable;

/**
 * The markers and taxi routes of a level, as the autopilot looks them up. Lookups return the first
 * match in placement order; the level's own is common's {@code AirfieldData}, and tests script their own.
 */
public interface Airfield {

    @Nullable
    Marker findById(MarkerType type, String id);

    /** A marker of the given type and id, preferring one of the given base. */
    @Nullable
    Marker findById(MarkerType type, String id, String baseId);

    /** A marker of any type with this id, preferring one of the given base; an empty id finds nothing. */
    @Nullable
    Marker findAnyById(@Nullable String id, String baseId);

    @Nullable
    Marker findByBase(String baseId, MarkerType type);

    /** A node mission's reference: the base's marker of that type if it names a base, else the marker itself. */
    @Nullable
    Marker resolveMarker(String nodeId, MarkerType type);

    /** The first parking spot of the base no aircraft is standing on. */
    @Nullable
    Marker findFreeParking(String baseId);

    List<Marker> markers();

    @Nullable
    TaxiRoute findRoute(String routeId);

    @Nullable
    TaxiRoute findRouteFromParking(String parkingId);

    @Nullable
    TaxiRoute findRouteToRunway(String runwayId);
}
