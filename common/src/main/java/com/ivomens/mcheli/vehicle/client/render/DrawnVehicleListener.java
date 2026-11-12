package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;

/**
 * Told about every vehicle the renderer draws, with where it was drawn this frame. Overlays that follow
 * entities on screen (team markers, the launcher sight) collect positions this way, since vehicles do not
 * go through the vanilla living-entity render hooks.
 */
@FunctionalInterface
public interface DrawnVehicleListener {

    /** The vehicle was drawn with its hull origin at {@code (x, y, z)}, relative to the camera. */
    void drawn(AircraftEntity vehicle, double x, double y, double z);
}
