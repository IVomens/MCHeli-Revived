package com.ivomens.mcheli.combat.weapon.net;

import javax.annotation.Nullable;

/**
 * What a radar contact is, as far as the radar instruments care: the server decides it once when it
 * snapshots the entity ({@code radar.EntityInfo#of}), and the instruments switch on it.
 * <p>
 * Sent as its ordinal, so constants are only ever appended; reordering them is a protocol change.
 */
public enum RadarContactKind {
    HELICOPTER,
    /** Fixed-wing aircraft, vertical take-off planes and plane drones included. */
    PLANE,
    SHIP,
    TANK,
    /** Wheeled ground vehicles and emplacements. */
    GROUND_VEHICLE,
    /** A missile fired by an {@code aamissile} weapon, which the BVR display warns of. */
    AIR_TO_AIR_MISSILE,
    /** Any other guided missile (air-to-surface, anti-tank, TV). */
    OTHER_MISSILE,
    CHAFF,
    FLARE,
    /** Players and anything else. */
    OTHER;

    private static final RadarContactKind[] VALUES = values();

    /** The kind sent as {@code id}, or null when no kind has that id. */
    @Nullable
    public static RadarContactKind byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** What the kind is sent as. */
    public int id() {
        return this.ordinal();
    }

    /** A crewed or drone vehicle of any kind. */
    public boolean isVehicle() {
        return switch (this) {
            case HELICOPTER, PLANE, SHIP, TANK, GROUND_VEHICLE -> true;
            case AIR_TO_AIR_MISSILE, OTHER_MISSILE, CHAFF, FLARE, OTHER -> false;
        };
    }

    /** Chaff or a flare. */
    public boolean isDecoy() {
        return this == CHAFF || this == FLARE;
    }
}
