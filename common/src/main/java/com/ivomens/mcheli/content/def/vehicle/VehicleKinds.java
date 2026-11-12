package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentType;

/** What the mod itself knows about a {@link VehicleKind} beyond the public enum: its content type and save name. */
public final class VehicleKinds {

    private VehicleKinds() {
    }

    /** The content type whose folder the kind's files are loaded from. */
    public static ContentType contentType(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> ContentType.HELICOPTER;
            case PLANE -> ContentType.PLANE;
            case SHIP -> ContentType.SHIP;
            case TANK -> ContentType.TANK;
            case VEHICLE -> ContentType.VEHICLE;
        };
    }

    /** The name the entity's persistent data has always used for this kind. */
    public static String legacyEntityType(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER, PLANE -> "Plane";
            case SHIP -> "Ship";
            case TANK, VEHICLE -> "Vehicle";
        };
    }
}
