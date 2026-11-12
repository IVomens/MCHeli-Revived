package com.ivomens.mcheli.wingman.airfield;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.core.wingman.airfield.Airfield;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The airfields of one level: its marker blocks and taxi routes. Both are saved with the level, each
 * in the file it has always had ({@code wingman_markers.dat}, {@code wingman_taxi_routes.dat}).
 *
 * <p>Markers form a simple parent/child model: a BASE marker (id "alpha") owns RUNWAY_A, RUNWAY_B,
 * PARKING and HELIPAD markers whose base id is "alpha". Marker ids are not unique across bases, so
 * lookups that know the base prefer that base's marker. Node missions refer to runways by base name or
 * by marker id; see {@link #resolveMarker}.
 *
 * <p>Lookups return the first match in placement order, as they always have; the autopilot depends
 * on it when several markers share an id.
 */
public final class AirfieldData implements Airfield {

    /** An aircraft within this horizontal distance of a parking spot occupies it. */
    private static final double OCCUPIED_RADIUS = 8.0;

    private final ServerLevel level;
    private final MarkerStore markers;
    private final TaxiRouteStore routes;

    private AirfieldData(ServerLevel level, MarkerStore markers, TaxiRouteStore routes) {
        this.level = level;
        this.markers = markers;
        this.routes = routes;
    }

    public static AirfieldData of(ServerLevel level) {
        return new AirfieldData(level, MarkerStore.of(level), TaxiRouteStore.of(level));
    }

    // Markers

    /** Records the marker at its position, replacing the one that was there. */
    public void putMarker(Marker marker) {
        this.markers.put(marker);
        McHeliWingman.logger.debug("[Airfield] marker {} at {} id={} base={}", marker.type(), Markers.pos(marker),
                marker.id(), marker.baseId());
    }

    public void removeMarker(BlockPos pos) {
        this.markers.remove(pos);
    }

    public void renameMarker(BlockPos pos, String id) {
        this.markers.rename(pos, id);
    }

    /** Every marker of the level, read-only, in placement order. */
    @Override
    public List<Marker> markers() {
        return this.markers.markers();
    }

    @Override
    public @Nullable Marker findById(MarkerType type, String id) {
        return findById(type, id, "");
    }

    /**
     * A marker of the given type and id. When {@code baseId} is non-empty a marker of that base wins;
     * otherwise the first match is returned.
     */
    @Override
    public @Nullable Marker findById(MarkerType type, String id, String baseId) {
        Marker fallback = null;
        for (Marker m : this.markers.markers()) {
            if (m.type() != type || !id.equals(m.id())) {
                continue;
            }
            if (!baseId.isEmpty() && baseId.equals(m.baseId())) {
                return m;
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        return fallback;
    }

    /**
     * Like {@link #findById(MarkerType, String, String)} for markers of any type. An empty id never
     * matches, so unnamed markers are not picked up by accident.
     */
    @Override
    public @Nullable Marker findAnyById(@Nullable String id, String baseId) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Marker fallback = null;
        for (Marker m : this.markers.markers()) {
            if (!id.equals(m.id())) {
                continue;
            }
            if (!baseId.isEmpty() && baseId.equals(m.baseId())) {
                return m;
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        return fallback;
    }

    public List<Marker> findByType(MarkerType type) {
        List<Marker> result = new ArrayList<>();
        for (Marker m : this.markers.markers()) {
            if (m.type() == type) {
                result.add(m);
            }
        }
        return result;
    }

    /** The first marker of the given type that belongs to the base. */
    @Override
    public @Nullable Marker findByBase(String baseId, MarkerType type) {
        for (Marker m : this.markers.markers()) {
            if (m.type() == type && baseId.equals(m.baseId())) {
                return m;
            }
        }
        return null;
    }

    /** Every marker that belongs to the base. */
    public List<Marker> findChildren(String baseId) {
        List<Marker> result = new ArrayList<>();
        for (Marker m : this.markers.markers()) {
            if (!m.baseId().isEmpty() && baseId.equals(m.baseId())) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public @Nullable Marker findFreeParking(String baseId) {
        return findAvailableParking(baseId, AircraftIndex.of(this.level).aircraft());
    }

    private @Nullable Marker findAvailableParking(String baseId, Iterable<? extends Entity> aircraft) {
        for (Marker m : this.markers.markers()) {
            if (m.type() == MarkerType.PARKING && baseId.equals(m.baseId()) && !isOccupied(m, aircraft)) {
                return m;
            }
        }
        return null;
    }

    private static boolean isOccupied(Marker marker, Iterable<? extends Entity> entities) {
        double px = marker.centerX();
        double pz = marker.centerZ();
        for (Entity e : entities) {
            if (!(e instanceof AircraftEntity)) {
                continue;
            }
            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            if (dx * dx + dz * dz < OCCUPIED_RADIUS * OCCUPIED_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a node-mission reference. If {@code nodeId} names a BASE marker, its child of the given
     * type is returned; otherwise {@code nodeId} is the marker id itself.
     */
    @Override
    public @Nullable Marker resolveMarker(String nodeId, MarkerType type) {
        if (findById(MarkerType.BASE, nodeId) != null) {
            Marker child = findByBase(nodeId, type);
            if (child != null) {
                return child;
            }
        }
        return findById(type, nodeId);
    }

    // Taxi routes

    /** Saves the route under its id, replacing a route with the same id. */
    public void saveRoute(TaxiRoute route) {
        this.routes.put(route);
        McHeliWingman.logger.info("[TaxiRoute] SAVE routeId={} baseId={} parkingId={} runwayId={} runwayBId={} "
                        + "wps={} heading={}", route.routeId(), route.baseId(), route.parkingId(), route.runwayId(),
                route.runwayBId(), route.waypointIds(), route.parkingHeading());
    }

    public void deleteRoute(String routeId) {
        this.routes.remove(routeId);
    }

    @Override
    public @Nullable TaxiRoute findRoute(String routeId) {
        return this.routes.get(routeId);
    }

    public List<TaxiRoute> routesOfBase(String baseId) {
        List<TaxiRoute> result = new ArrayList<>();
        for (TaxiRoute r : this.routes.routes()) {
            if (baseId.equals(r.baseId())) {
                result.add(r);
            }
        }
        return result;
    }

    /** The first route that starts at the given parking spot. */
    @Override
    public @Nullable TaxiRoute findRouteFromParking(String parkingId) {
        for (TaxiRoute r : this.routes.routes()) {
            if (parkingId.equals(r.parkingId())) {
                return r;
            }
        }
        return null;
    }

    /**
     * The first route whose runway end is the given marker. After a VTOL landing the helipad id is the
     * route's {@code runwayId}, so this finds the route back to parking.
     */
    @Override
    public @Nullable TaxiRoute findRouteToRunway(String runwayId) {
        for (TaxiRoute r : this.routes.routes()) {
            if (runwayId.equals(r.runwayId())) {
                return r;
            }
        }
        return null;
    }
}
