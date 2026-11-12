package com.ivomens.mcheli.api.vehicle;

/**
 * The kinds of vehicle MCHeli knows. Each kind has its own content folder, and vehicle ids are unique only
 * within one kind, so a lookup always takes both.
 *
 * <p>Immutable; usable from any thread and on either side. Stable: constants are never removed or
 * renamed within a major API version, but a minor version may add one, so a {@code switch} over this enum
 * needs a {@code default} branch.
 */
public enum VehicleKind {
    HELICOPTER("helicopters"),
    PLANE("planes"),
    SHIP("ships"),
    TANK("tanks"),
    VEHICLE("vehicles");

    private final String directory;

    VehicleKind(String directory) {
        this.directory = directory;
    }

    /**
     * The folder a content pack keeps the kind's files in, below {@code assets/mcheli/}, for example
     * {@code helicopters}. The same name is the kind's model folder.
     */
    public String directory() {
        return this.directory;
    }
}
