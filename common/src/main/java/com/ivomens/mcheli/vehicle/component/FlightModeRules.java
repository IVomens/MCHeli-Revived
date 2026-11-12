package com.ivomens.mcheli.vehicle.component;

/**
 * What a vehicle type adds to the shared rules of {@link FlightModesComponent}. Each vehicle type
 * hands one to its modes when it is built; everything defaults to the shared behaviour.
 */
public interface FlightModeRules {

    FlightModeRules SHARED = new FlightModeRules() {
    };

    /** Conditions for gunner mode on top of the shared ones (gear, canopy, copilot). */
    default boolean allowsGunnerMode() {
        return true;
    }

    /** Conditions for hover mode on top of the shared ones. */
    default boolean allowsHovering() {
        return true;
    }

    default boolean allowsFreeLook() {
        return true;
    }

    /** Every seat looks through the gunner camera, as on emplaced guns and light vehicles. */
    default boolean alwaysCameraView() {
        return false;
    }

    /** While hovering the pilot may look up and down without pitching the airframe. */
    default boolean hoverFreesPitch() {
        return false;
    }

    /** While hovering the pilot may look around without turning the airframe. */
    default boolean hoverFreesYaw() {
        return false;
    }
}
