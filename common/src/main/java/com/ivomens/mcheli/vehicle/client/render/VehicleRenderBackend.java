package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.RenderSettings;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.render.flywheel.FlywheelVehicleVisualizer;
import com.ivomens.mcheli.vehicle.client.render.flywheel.FlywheelVehicles;

/**
 * Chooses the backend that draws each vehicle's model.
 * <p>
 * Flywheel draws the models when it is installed, its visualization is supported in the level (it
 * turns itself off under shader packs and on hardware it cannot use) and the config allows it. The
 * default, {@code VehicleRenderer = immediate}, never uses it. {@code auto} uses it only on Flywheel's
 * indirect backend, which draws all instances of a material with one indirect draw: its instancing
 * backend (OpenGL below 4.6, so every Mac) issues one draw call per part model with its own state
 * changes and instance buffer, and a mixed fleet has hundreds of part models, which ran a MacBook at
 * under 1 FPS where the built-in renderer drew 40 (see docs/RENDERING.md). {@code flywheel} uses it
 * on any backend, for testing. Each Flywheel visual reports whether it drew its vehicle this frame;
 * every vehicle it did not is drawn by {@link ImmediateVehicleRenderer}, so a vehicle is never left
 * undrawn while the backends hand over.
 * <p>
 * Flywheel is an optional dependency: none of its classes are touched unless it is installed.
 */
public final class VehicleRenderBackend {

    private static final String FLYWHEEL_MOD_ID = "flywheel";
    private static final boolean FLYWHEEL_INSTALLED = Services.PLATFORM.isModLoaded(FLYWHEEL_MOD_ID);

    private VehicleRenderBackend() {
    }

    /** Registers the Flywheel visualizers for the vehicle entity types when Flywheel is installed. */
    public static void registerVisualizers() {
        if (FLYWHEEL_INSTALLED) {
            FlywheelVehicleVisualizer.register();
            ModLogger.info("Flywheel is installed; vehicle models are instanced where it is active.");
        }
    }

    /**
     * Whether Flywheel should draw vehicle models: the config allows it, and with {@code auto} Flywheel
     * runs its indirect backend. Any thread; only called when Flywheel is installed.
     */
    public static boolean isFlywheelAllowed() {
        RenderSettings render = McheliConfig.render();
        if (render.forcesFlywheelVehicleRenderer()) {
            return true;
        }
        return render.isAutoVehicleRenderer() && FlywheelVehicleVisualizer.isIndirectBackend();
    }

    /**
     * Whether this vehicle's model is drawn by its Flywheel visual this frame: Flywheel supports
     * visualization in the vehicle's level and the visual has drawn it.
     */
    public static boolean isDrawnByFlywheel(AircraftEntity vehicle) {
        return FLYWHEEL_INSTALLED && FlywheelVehicleVisualizer.supportsVisualization(vehicle.level())
                && FlywheelVehicles.isDrawn(vehicle);
    }
}
