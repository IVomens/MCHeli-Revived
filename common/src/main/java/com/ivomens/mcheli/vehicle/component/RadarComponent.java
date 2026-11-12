package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.Radar;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.core.math.Vec2d;

import java.util.List;

/**
 * The vehicle's radars: the switch the crew flips (synced), the entity scope on the HUD with its
 * sweep, and how other vehicles are labelled for this vehicle's radar type.
 */
public final class RadarComponent implements VehicleComponent {

    private static final int SCOPE_RANGE = 64;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final Radar scope;
    private int sweepAngle;

    public RadarComponent(AircraftEntity vehicle, StatusWord status) {
        this.vehicle = vehicle;
        this.status = status;
        this.scope = new Radar(vehicle.level());
    }

    public boolean isOn() {
        return this.status.get(StatusWord.RADAR);
    }

    /** Server: switches the radar on or off. */
    public void setOn(boolean on) {
        this.status.set(StatusWord.RADAR, on);
    }

    /** The crew's client switches its own radar right away, ahead of the server. */
    public void predictOn(boolean on) {
        this.status.set(StatusWord.RADAR, on, true);
    }

    /** The vehicle has an entity scope. */
    public boolean hasScope() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && info.file().features().entityRadar();
    }

    /** Turns the sweep; the contacts are refreshed once per full turn. */
    public void sweep(int degreesPerTick) {
        if (!hasScope()) {
            return;
        }
        this.sweepAngle += degreesPerTick;
        if (this.sweepAngle >= 360) {
            this.sweepAngle = 0;
        }
        if (this.sweepAngle == 0) {
            this.scope.updateXZ(this.vehicle, SCOPE_RANGE);
        }
    }

    public void reset() {
        this.scope.clear();
        this.sweepAngle = 0;
    }

    public int sweepAngle() {
        return this.sweepAngle;
    }

    @Override
    public void describe(StateSink state) {
        state.put("on", isOn());
        state.put("scope", hasScope());
        state.put("sweep angle", this.sweepAngle);
    }

    public List<Vec2d> contacts() {
        return this.scope.getEntityList();
    }

    public List<Vec2d> enemies() {
        return this.scope.getEnemyList();
    }

    /** How another vehicle is labelled on this vehicle's radar type. */
    public String labelFor(AircraftEntity other) {
        VehicleSpec info = this.vehicle.spec();
        VehicleSpec otherInfo = other.spec();
        if (otherInfo == null || info == null) {
            return "";
        }
        return switch (info.file().features().radar().type()) {
            case MODERN_AA -> otherInfo.file().radarNames().modernAirToAir();
            case EARLY_AA -> otherInfo.file().radarNames().earlyAirToAir();
            case MODERN_AS -> otherInfo.file().radarNames().modernAirToSurface();
            case EARLY_AS -> otherInfo.file().radarNames().earlyAirToSurface();
            case null -> "";
        };
    }

    /** How the vehicle with the entity id is labelled on this vehicle's radar type; "?" for any other entity. */
    public String labelFor(int entityId) {
        if (this.vehicle.level().getEntity(entityId) instanceof AircraftEntity aircraft) {
            return labelFor(aircraft);
        }
        return "?";
    }

    /** How this vehicle is labelled on the other vehicle's radar type. */
    public String labelOnRadarOf(AircraftEntity other) {
        VehicleSpec info = this.vehicle.spec();
        VehicleSpec otherInfo = other.spec();
        if (otherInfo == null || info == null) {
            return "?";
        }
        return switch (otherInfo.file().features().radar().type()) {
            case MODERN_AA -> info.file().radarNames().modernAirToAir();
            case EARLY_AA -> info.file().radarNames().earlyAirToAir();
            case MODERN_AS -> info.file().radarNames().modernAirToSurface();
            case EARLY_AS -> info.file().radarNames().earlyAirToSurface();
            default -> "?";
        };
    }
}
