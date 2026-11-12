package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.client.render.PoseRecorder;
import com.ivomens.mcheli.vehicle.client.render.VehicleInstanceTransforms;
import com.ivomens.mcheli.vehicle.client.render.VehicleModelRenderer;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderBackend;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot;
import com.ivomens.mcheli.vehicle.client.render.VehicleSnapshots;
import com.ivomens.mcheli.vehicle.client.render.VehicleTextures;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Flywheel backend for one vehicle. Its parts are {@link VehicleInstances}: instances of shared part
 * models drawn straight from the render origin, so all vehicles share one instancer per model.
 * Flywheel's instancing backend issues one draw call per instancer; with an embedding per vehicle (as
 * this backend first did) every vehicle had instancers of its own, a draw call per part and vehicle. A
 * part's instance is only rewritten when its matrix, tint or light changes, so the parts of a parked
 * vehicle are written once. While the config does not let Flywheel draw, the visual holds no instances.
 * <p>
 * The level of detail picks one of the reduced meshes for the whole vehicle by its distance, see
 * {@link FlywheelModels#detailLevel}. Small parts are dropped at a distance exactly as the immediate
 * renderer drops them.
 * <p>
 * Every frame the visual takes its own {@link VehicleRenderSnapshot} on a Flywheel worker; effects and
 * markers are left to the entity renderer, which also draws the model itself whenever this visual
 * does not (see {@link VehicleRenderBackend}).
 */
final class FlywheelVehicleVisual extends AbstractEntityVisual<AircraftEntity>
        implements SimpleDynamicVisual, VehicleModelRenderer {

    private final PoseRecorder recorder = new PoseRecorder();
    private final Matrix4f hullMatrix = new Matrix4f();
    private final VehicleInstances parts;
    private boolean drawn;

    FlywheelVehicleVisual(VisualizationContext context, AircraftEntity vehicle, float partialTick) {
        super(context, vehicle, partialTick);
        this.parts = new VehicleInstances(context, InstanceTypes.TRANSFORMED);
        FlywheelVehicleStats.visualCreated();
        // Flywheel recreates every visual when it moves its render origin, and runs no frame for them
        // in that frame; drawn at once, the vehicle is not handed to the immediate renderer for a frame
        // and its models' instancers are never empty, which would free (and re-upload) their meshes
        if (VehicleRenderBackend.isFlywheelAllowed()) {
            this.draw(partialTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        }
    }

    @Override
    public void beginFrame(Context context) {
        FlywheelVehicleStats.frame();
        boolean allowed = VehicleRenderBackend.isFlywheelAllowed();
        ModelKeepers.sweep(allowed);
        if (!allowed) {
            // the immediate renderer draws: give everything back, or Flywheel keeps drawing (empty) instancers
            this.deleteInstances();
            return;
        }
        this.draw(context.partialTick(), context.camera().getPosition());
    }

    /** Takes the vehicle's snapshot and updates its instances, or parks them without one. */
    private void draw(float partialTick, Vec3 camera) {
        ResourceLocation texture = VehicleTextures.published(this.entity);
        VehicleSnapshots.View view = new VehicleSnapshots.View(partialTick, camera,
                VehicleSnapshots.lastPixelsAtOneBlock());
        VehicleRenderSnapshot snapshot = texture == null ? null
                : VehicleSnapshots.capture(this.entity, view, texture, this.computePackedLight(partialTick));
        if (snapshot == null) {
            this.park();
            return;
        }
        this.render(snapshot);
    }

    /** Brings the part instances up to date with the snapshot. */
    @Override
    public void render(VehicleRenderSnapshot snapshot) {
        VehicleInstanceTransforms.hullMatrix(snapshot.hull(), this.renderOrigin(), this.recorder, this.hullMatrix);
        VehicleInstanceTransforms.partMatrices(snapshot, this.recorder);
        boolean drawn = this.parts.update(this.recorder, this.hullMatrix,
                FlywheelModels.detailLevel(snapshot.detail().unitsPerPixel()), snapshot.texture(),
                snapshot.tint().argb(), snapshot.light());
        this.setDrawn(drawn);
    }

    /** Parks every instance and hands the vehicle back to the immediate renderer. */
    private void park() {
        this.parts.park();
        this.setDrawn(false);
    }

    @Override
    public void update(float partialTick) {
    }

    @Override
    protected void _delete() {
        this.deleteInstances();
        FlywheelVehicleStats.visualDeleted();
    }

    /** Deletes every instance and hands the vehicle back to the immediate renderer. */
    private void deleteInstances() {
        this.parts.delete();
        this.setDrawn(false);
    }

    /** Tells the entity renderer, only when it changed: the set is shared by every worker thread. */
    private void setDrawn(boolean drawn) {
        if (drawn != this.drawn) {
            this.drawn = drawn;
            FlywheelVehicles.setDrawn(this.entity, drawn);
        }
    }
}
