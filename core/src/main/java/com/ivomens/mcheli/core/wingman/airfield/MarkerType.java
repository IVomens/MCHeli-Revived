package com.ivomens.mcheli.core.wingman.airfield;

import java.util.Locale;

/**
 * Role of a wingman marker block. Saved by name in NBT; the block state names it by
 * {@link #serializedName()}.
 */
public enum MarkerType {
    /** A base: the anchor the other markers belong to through their base id. */
    BASE("§6", "Base"),
    PARKING("§e", "Parking"),
    /** Runway threshold: start of the takeoff roll and touchdown point. */
    RUNWAY_A("§a", "Runway-A"),
    /** Far runway end: end of the takeoff roll and of the landing roll. */
    RUNWAY_B("§b", "Runway-B"),
    /** Taxi waypoint; only X and Z are used. */
    WAYPOINT("§d", "Waypoint"),
    /** Vertical takeoff and landing spot for helicopters and VTOL planes. A base may have several. */
    HELIPAD("§9", "Helipad"),
    /**
     * Heading reference for helipads of the same base: HELIPAD to HELIPAD_B gives the nose direction for
     * vertical takeoff and landing, like RUNWAY_A to RUNWAY_B for a runway. Not a landing spot itself.
     */
    HELIPAD_B("§3", "Helipad-B");

    private final String color;
    private final String shortName;

    MarkerType(String color, String shortName) {
        this.color = color;
        this.shortName = shortName;
    }

    /** The lower-case name the marker block's state uses, for example {@code runway_a}. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Coloured chat label, for example "§a[Runway-A]". */
    public String displayName() {
        return color + "[" + shortName + "]";
    }

    /** Plain name for GUI buttons. */
    public String shortName() {
        return shortName;
    }
}
