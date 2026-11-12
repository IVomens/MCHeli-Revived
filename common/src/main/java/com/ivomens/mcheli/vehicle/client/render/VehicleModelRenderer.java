package com.ivomens.mcheli.vehicle.client.render;

/**
 * A way of drawing vehicle models. Two backends exist:
 * <ul>
 *     <li>{@link ImmediateVehicleRenderer} streams every vertex through the vanilla buffers each
 *     frame. It always works, including under shader packs, and is the reference the other backend
 *     is checked against.</li>
 *     <li>{@code FlywheelVehicleVisual} keeps one instance per part on the GPU and only updates the
 *     ones that moved. It is used when Flywheel is installed and active.</li>
 * </ul>
 * Effects (search light cones, ropes, exhaust flames, markers) are not part of the model and are
 * always drawn immediately, by {@link VehicleEffectsRenderer}.
 */
public interface VehicleModelRenderer {

    /** Draws, or brings up to date, the vehicle's model as the snapshot describes it. */
    void render(VehicleRenderSnapshot snapshot);
}
