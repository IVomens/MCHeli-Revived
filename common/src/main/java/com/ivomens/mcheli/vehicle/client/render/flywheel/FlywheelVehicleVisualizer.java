package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Creates a {@link FlywheelVehicleVisual} for every vehicle Flywheel visualizes. The vanilla entity
 * renderer keeps running: it draws the effects and markers, and the model whenever the visual does
 * not. Only reached when Flywheel is installed.
 */
public final class FlywheelVehicleVisualizer implements EntityVisualizer<AircraftEntity> {

    private static final FlywheelVehicleVisualizer INSTANCE = new FlywheelVehicleVisualizer();
    private static final ResourceLocation INDIRECT = ResourceLocation.fromNamespaceAndPath("flywheel", "indirect");

    private FlywheelVehicleVisualizer() {
    }

    /** Registers the visualizer for every vehicle entity type. */
    public static void register() {
        VisualizerRegistry.setVisualizer(AircraftEntityTypes.HELI.get(), INSTANCE);
        VisualizerRegistry.setVisualizer(AircraftEntityTypes.PLANE.get(), INSTANCE);
        VisualizerRegistry.setVisualizer(AircraftEntityTypes.SHIP.get(), INSTANCE);
        VisualizerRegistry.setVisualizer(AircraftEntityTypes.TANK.get(), INSTANCE);
        VisualizerRegistry.setVisualizer(AircraftEntityTypes.VEHICLE.get(), INSTANCE);
    }

    /**
     * Whether Flywheel runs its indirect backend, the one that draws a material's instances of every
     * model with one indirect draw. Its instancing backend issues a draw call per part model.
     */
    public static boolean isIndirectBackend() {
        Backend indirect = Backend.REGISTRY.get(INDIRECT);
        return indirect != null && BackendManager.currentBackend() == indirect;
    }

    /** Whether Flywheel visualizes the level: false under shader packs or without a usable backend. */
    public static boolean supportsVisualization(Level level) {
        return VisualizationManager.supportsVisualization(level);
    }

    @Override
    public EntityVisual<? super AircraftEntity> createVisual(VisualizationContext context, AircraftEntity vehicle,
                                                            float partialTick) {
        return new FlywheelVehicleVisual(context, vehicle, partialTick);
    }

    @Override
    public boolean skipVanillaRender(AircraftEntity vehicle) {
        return false;
    }
}
