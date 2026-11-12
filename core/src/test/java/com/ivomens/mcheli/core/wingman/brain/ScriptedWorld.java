package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Airfield;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A world written by the test: flat ground at a fixed height, the markers and routes the test places,
 * no hostiles and no siblings unless it says so. It answers both the brain's senses and its airfield
 * lookups, and remembers what the brain told it.
 */
final class ScriptedWorld implements Senses, Airfield {

    static final Tuning FORMATION = new Tuning(20.0, 0.0, 30.0, 0.0, 0.0);

    double ground = 64;
    final List<Point> siblings = new ArrayList<>();
    @Nullable Point target;
    int escapeTicks;
    boolean hostileNearby;
    int hostiles;
    final List<Marker> markers = new ArrayList<>();
    final List<TaxiRoute> routes = new ArrayList<>();
    final List<String> reports = new ArrayList<>();

    Perception perceive(Airframe airframe, @Nullable LeaderState leader) {
        return new Perception("test", airframe, leader, this, this, FORMATION, BrainLog.NONE);
    }

    static Airframe plane() {
        return new Airframe(Airframe.Kind.PLANE, false, 100, 100, Airframe.DEFAULT_GRAVITY, 0.0F);
    }

    static Airframe helicopter() {
        return new Airframe(Airframe.Kind.HELICOPTER, false, 100, 100, Airframe.DEFAULT_GRAVITY, 0.65F);
    }

    static RecordedControls at(double x, double y, double z, float yaw) {
        return new RecordedControls(new Point(x, y, z), new Point(0, 0, 0), yaw, 0.0F, 0.0F, 0.0, true, false);
    }

    Marker place(MarkerType type, String id, String baseId, int x, int y, int z) {
        Marker marker = new Marker(x, y, z, type, id, baseId, -1);
        this.markers.add(marker);
        return marker;
    }

    // Senses

    @Override
    public List<Point> siblings() {
        return this.siblings;
    }

    @Override
    public boolean consumeEscapeTick() {
        return this.escapeTicks-- > 0;
    }

    @Override
    public @Nullable Point attackTarget() {
        return this.target;
    }

    @Override
    public double groundHeight(int x, int z) {
        return this.ground;
    }

    @Override
    public boolean hostileWithin(double radius) {
        return this.hostileNearby;
    }

    @Override
    public int hostilesAround(double x, double z, double radius) {
        return this.hostiles;
    }

    @Override
    public void showAutopilot(float yaw, float throttle) {
    }

    @Override
    public void keepAutopilotShown() {
    }

    @Override
    public void reportRecon(int count, String reason) {
        this.reports.add(count + " " + reason);
    }

    // Airfield

    @Override
    public @Nullable Marker findById(MarkerType type, String id) {
        return findById(type, id, "");
    }

    @Override
    public @Nullable Marker findById(MarkerType type, String id, String baseId) {
        return this.markers.stream().filter(m -> m.type() == type && m.id().equals(id)).findFirst().orElse(null);
    }

    @Override
    public @Nullable Marker findAnyById(@Nullable String id, String baseId) {
        return this.markers.stream().filter(m -> m.id().equals(id) && !m.id().isEmpty()).findFirst().orElse(null);
    }

    @Override
    public @Nullable Marker findByBase(String baseId, MarkerType type) {
        return this.markers.stream().filter(m -> m.type() == type && m.baseId().equals(baseId)).findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable Marker resolveMarker(String nodeId, MarkerType type) {
        Marker byBase = findByBase(nodeId, type);
        return byBase != null ? byBase : findById(type, nodeId);
    }

    @Override
    public @Nullable Marker findFreeParking(String baseId) {
        return findByBase(baseId, MarkerType.PARKING);
    }

    @Override
    public List<Marker> markers() {
        return this.markers;
    }

    @Override
    public @Nullable TaxiRoute findRoute(String routeId) {
        return this.routes.stream().filter(r -> r.routeId().equals(routeId)).findFirst().orElse(null);
    }

    @Override
    public @Nullable TaxiRoute findRouteFromParking(String parkingId) {
        return this.routes.stream().filter(r -> r.parkingId().equals(parkingId)).findFirst().orElse(null);
    }

    @Override
    public @Nullable TaxiRoute findRouteToRunway(String runwayId) {
        return this.routes.stream().filter(r -> r.runwayId().equals(runwayId)).findFirst().orElse(null);
    }
}
