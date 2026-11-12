package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.LockOnMarkerRenderer;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import com.ivomens.mcheli.vehicle.client.VehicleModelRegistry;
import com.ivomens.mcheli.vehicle.client.debug.WeaponPointRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * The entity renderer of every vehicle type. Each frame it takes the vehicle's
 * {@link VehicleRenderSnapshot}, has the model drawn by the immediate backend unless a Flywheel visual
 * drew it (see {@link VehicleRenderBackend}), then draws the effects and the overlay markers, which
 * are always immediate.
 * <p>
 * Riders are drawn by their own renderers; see {@link VehicleRiderRendering}.
 */
public final class VehicleEntityRenderer<T extends AircraftEntity> extends McheliEntityRenderer<T> {

    private static final float SHADOW_RADIUS = 2.0F;

    private final VehicleTextures textures;
    private final DrawnVehicleListener drawnListener;

    private VehicleEntityRenderer(EntityRendererProvider.Context context, ThermalVision thermal,
                                  VehicleTextures textures, DrawnVehicleListener drawnListener) {
        super(context, thermal);
        this.shadowRadius = SHADOW_RADIUS;
        this.textures = textures;
        this.drawnListener = drawnListener;
    }

    /** Renderers for one vehicle type, sharing the client's textures and the listener of drawn vehicles. */
    public static <E extends AircraftEntity> EntityRendererProvider<E> provider(ThermalVision thermal,
            VehicleTextures textures, DrawnVehicleListener drawnListener) {
        return context -> new VehicleEntityRenderer<>(context, thermal, textures, drawnListener);
    }

    @Override
    public boolean shouldRender(@NotNull T entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        VehicleSpec info = entity.spec();
        double radius = 0.0;
        if (info != null && VehicleModelRegistry.of(info).body() instanceof BakedModelBase model) {
            radius = model.getBoundingRadius() * Math.max(1.0F,
                    Math.max(info.file().render().modelWidth(), info.file().render().modelHeight()));
        }
        return isVisible(entity, frustum, radius, camX, camY, camZ);
    }

    @Override
    public void render(@NotNull T entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight) {
        // the vehicle keeps its own smoothed light, full bright while a search light is on
        int vehicleLight = entity.lighting().packedLight();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, vehicleLight != 0 ? vehicleLight : packedLight);
    }

    @Override
    public void doRender(@NotNull T entity, double x, double y, double z, float entityYaw, float tickTime) {
        VehicleSpec info = entity.spec();
        if (info == null) {
            return;
        }
        if (OnDemandModels.isEnabled()) {
            // keeps the model resident, or starts loading it; nothing is drawn until it is ready
            OnDemandModels.notifyRendered(info);
        }
        if (entity instanceof GroundVehicleEntity vehicle) {
            VehicleFrameUpdates.aimGroundParts(vehicle);
        }
        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
        VehicleSnapshots.View view = new VehicleSnapshots.View(tickTime, camera, VehicleSnapshots.pixelsAtOneBlock());
        VehicleRenderSnapshot snapshot = VehicleSnapshots.capture(entity, view, this.textures.resolve(entity, info),
                this.packedLight);
        if (snapshot == null) {
            return;
        }
        VehicleEffects effects = VehicleEffects.capture(entity, info, snapshot, tickTime, this.thermal());

        LegacyGL.setDetailPixelsPerBlock(snapshot.detail().pixelsPerBlock());
        try {
            VehicleDebugViews.render(entity, snapshot);
            if (!VehicleRenderBackend.isDrawnByFlywheel(entity)) {
                ImmediateVehicleRenderer.INSTANCE.render(snapshot);
            }
            VehicleEffectsRenderer.render(snapshot, effects);
            LegacyGL.disableBlend();
        } finally {
            LegacyGL.setDetailPixelsPerBlock(Float.POSITIVE_INFINITY);
        }
        renderMarkers(entity, info, snapshot, camera, tickTime);
    }

    private void renderMarkers(AircraftEntity entity, VehicleSpec info, VehicleRenderSnapshot snapshot,
                               Vec3 camera, float tickTime) {
        double relX = snapshot.hull().x() - camera.x;
        double relY = snapshot.hull().y() - camera.y;
        double relZ = snapshot.hull().z() - camera.z;
        this.drawnListener.drawn(entity, relX, relY, relZ);
        LockOnMarkerRenderer.render(entity, tickTime);
        if (McheliConfig.debug().testMode()) {
            WeaponPointRenderer.renderWeaponPoints(entity, info, 0.0, 0.0, 0.0);
        }
    }
}
