package com.ivomens.mcheli.api.event;

/**
 * The screens of a vehicle a player can open ({@link VehicleScreenOpenAttempt}). Since API 1.1: constants are
 * never removed within a major version, but a minor one may add some, so a {@code switch} over this enum needs
 * a {@code default} branch.
 */
public enum VehicleScreen {
    /** The pilot's panel: fuel, ammunition and reloading, crew settings, and the cargo. */
    PANEL,
    /** The cargo inventory alone, which a sneak-click on the vehicle opens. */
    CARGO
}
