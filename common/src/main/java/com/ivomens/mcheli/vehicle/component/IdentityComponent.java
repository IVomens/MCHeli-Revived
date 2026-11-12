package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;

/**
 * Which vehicle this is: the name of the type it is made from, which every client sees, and the unique id
 * that ties its seats to it and that it keeps through saves and when it is picked up as an item.
 */
public final class IdentityComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final SyncedValue<String> typeName;
    private String uniqueId = "";

    public IdentityComponent(AircraftEntity vehicle, SyncedValue<String> typeName) {
        this.vehicle = vehicle;
        this.typeName = typeName;
    }

    /** The file name of the vehicle's type, or an empty string before it is known. */
    public String typeName() {
        return this.typeName.get();
    }

    /**
     * Makes the vehicle one of the named type: loads the type and points everything aimable the way the hull
     * faces. An empty name, or the type it already is, changes nothing.
     */
    public void setTypeName(String name) {
        if (name != null && !name.isEmpty() && !name.equals(this.typeName())) {
            this.typeName.set(name);
            this.vehicle.changeType(name);
            this.vehicle.initRotationYaw(this.vehicle.getYRot());
        }
    }

    /**
     * Loads the type the synced name points at, the first time it is missing; returns whether it was already
     * there. The tick that loads it does nothing else of the type's.
     */
    public boolean loadTypeIfMissing() {
        if (this.vehicle.spec() != null) {
            return true;
        }
        this.vehicle.changeType(this.typeName());
        this.vehicle.xo = this.vehicle.getX();
        this.vehicle.yo = this.vehicle.getY();
        this.vehicle.zo = this.vehicle.getZ();
        return false;
    }

    /** The id that ties the vehicle's seats to it; empty until it is given one. */
    public String uniqueId() {
        return this.uniqueId;
    }

    public void setUniqueId(String id) {
        this.uniqueId = id != null ? id : "";
    }

    @Override
    public void describe(StateSink state) {
        state.put("type", typeName().isEmpty() ? "-" : typeName());
        state.put("unique id", this.uniqueId.isEmpty() ? "-" : this.uniqueId);
    }

    /** The translation key of the vehicle's type name, or an empty string before the type is known. */
    public String translationKey() {
        return this.vehicle.spec() != null ? "item.mcheli:" + this.vehicle.spec().name() + ".name" : "";
    }
}
