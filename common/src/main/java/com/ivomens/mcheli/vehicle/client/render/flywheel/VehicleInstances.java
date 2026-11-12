package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.client.model.MeshDetail;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.vehicle.client.render.PoseRecorder;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Flywheel instances of one vehicle's parts. Every part is an instance of its shared part model
 * ({@link FlywheelModels}) in the visualization's own instancers, placed from the render origin with
 * the hull and part matrices multiplied together, so every vehicle using a model adds instances to the
 * same instancer: one draw call per model, however many vehicles. Instances live as long as the
 * vehicle; a frame only rewrites the instances whose matrix, tint or light changed.
 * <p>
 * A steady frame makes nothing: the models of each part are looked up once (when the part's mesh or
 * the texture changes), the instances of a level of detail stay parked while the vehicle uses the
 * other one, so a vehicle crossing back and forth only moves instances, and a vehicle that goes gives
 * its instances back to {@link ModelKeepers} for the next one. The far level's models are only looked
 * up when a vehicle first needs them: made in advance for every part they would double the instancers,
 * and so the draw calls of Flywheel's instancing backend, for a level that is rarely drawn on a big
 * screen. A part whose model is not resident yet waits for {@link ModelKeepers} to add it, together with
 * the other new ones; meanwhile the vehicle is left to the immediate renderer.
 */
final class VehicleInstances {

    private final VisualizationContext context;
    private final InstanceType<TransformedInstance> type;
    private final Matrix4f partMatrix = new Matrix4f();
    /** The instances of each model the vehicle has used, in drawing order. */
    private final Map<Model, ModelInstances> instances = new HashMap<>();
    /** The same, as a list to walk without an iterator. */
    private final List<ModelInstances> instanceList = new ArrayList<>();
    /** The models of each recorded part, by its index in the recorder. */
    private Slot[] slots = new Slot[0];
    @Nullable
    private ResourceLocation texture;
    private int lastColor;
    /** Some part of this frame waits for its model to become resident. */
    private boolean waiting;
    private int lastLight;

    /** Instances of {@code type} ({@link InstanceTypes#TRANSFORMED}; tests pass their own) from the context. */
    VehicleInstances(VisualizationContext context, InstanceType<TransformedInstance> type) {
        this.context = context;
        this.type = type;
    }

    /**
     * The models of one part at the full and the far level of detail, null where it has no faces, and
     * their instances in this vehicle, null until the model is resident (see {@link ModelKeepers#acquire}).
     * The far model is looked up when first needed.
     */
    private static final class Slot {

        private final PartMesh mesh;
        @Nullable
        private final Model fullModel;
        @Nullable
        private ModelInstances full;
        @Nullable
        private Model farModel;
        @Nullable
        private ModelInstances far;
        private boolean farLooked;

        Slot(PartMesh mesh, @Nullable Model fullModel) {
            this.mesh = mesh;
            this.fullModel = fullModel;
        }
    }

    /**
     * Places the parts the recorder holds (matrices relative to the hull) under {@code hull} (from the
     * render origin to the hull), at a level of detail and with a texture, tint and light; parks the
     * instances this frame does not need. Returns whether the vehicle is drawn: some part has an
     * instance, and no part waits for its model to become resident (then everything is parked and the
     * immediate renderer draws the vehicle).
     */
    boolean update(PoseRecorder parts, Matrix4fc hull, int detailLevel, ResourceLocation texture, int color,
                   int light) {
        boolean relight = color != this.lastColor || light != this.lastLight;
        this.lastColor = color;
        this.lastLight = light;
        if (!texture.equals(this.texture)) {
            this.texture = texture;
            Arrays.fill(this.slots, null);
        }
        for (int m = 0; m < this.instanceList.size(); m++) {
            this.instanceList.get(m).beginFrame();
        }
        boolean far = detailLevel != MeshDetail.FULL;
        boolean drawn = false;
        this.waiting = false;
        for (int i = 0; i < parts.size(); i++) {
            Slot slot = this.slot(i, parts.mesh(i), texture);
            ModelInstances models = far ? this.far(slot, texture) : this.full(slot);
            if (models != null) {
                hull.mul(parts.matrix(i), this.partMatrix);
                models.place(this.partMatrix, color, light, relight);
                drawn = true;
            }
        }
        if (this.waiting) {
            for (int m = 0; m < this.instanceList.size(); m++) {
                this.instanceList.get(m).beginFrame();
            }
        }
        for (int m = 0; m < this.instanceList.size(); m++) {
            this.instanceList.get(m).parkUnused();
        }
        return drawn && !this.waiting;
    }

    /** The models of part {@code index}, looked up again only when the part's mesh changed. */
    private Slot slot(int index, PartMesh mesh, ResourceLocation texture) {
        if (index >= this.slots.length) {
            this.slots = Arrays.copyOf(this.slots, Math.max(index + 1, this.slots.length * 2));
        }
        Slot slot = this.slots[index];
        if (slot == null || slot.mesh != mesh) {
            slot = new Slot(mesh, FlywheelModels.model(mesh, MeshDetail.FULL, texture));
            this.slots[index] = slot;
        }
        return slot;
    }

    @Nullable
    private ModelInstances full(Slot slot) {
        if (slot.full == null && slot.fullModel != null) {
            slot.full = this.modelInstances(slot.fullModel);
        }
        return slot.full;
    }

    @Nullable
    private ModelInstances far(Slot slot, ResourceLocation texture) {
        if (!slot.farLooked) {
            slot.farModel = FlywheelModels.model(slot.mesh, FlywheelModels.farLevel(), texture);
            slot.farLooked = true;
        }
        if (slot.far == null && slot.farModel != null) {
            slot.far = this.modelInstances(slot.farModel);
        }
        return slot.far;
    }

    /** The vehicle's instances of a model; null, and the vehicle waits, while the model is not resident. */
    @Nullable
    private ModelInstances modelInstances(Model model) {
        ModelInstances models = this.instances.get(model);
        if (models == null) {
            ModelKeepers.Keeper keeper = ModelKeepers.acquire(model, this.context, this.type);
            if (keeper == null) {
                this.waiting = true;
                return null;
            }
            models = new ModelInstances(keeper);
            this.instances.put(model, models);
            this.instanceList.add(models);
        }
        return models;
    }

    /** Parks every instance: the vehicle draws nothing this frame but keeps its instances. */
    void park() {
        for (int m = 0; m < this.instanceList.size(); m++) {
            ModelInstances models = this.instanceList.get(m);
            models.beginFrame();
            models.parkUnused();
        }
    }

    /** Gives every instance back to the keepers and lets go of the models. */
    void delete() {
        for (int m = 0; m < this.instanceList.size(); m++) {
            this.instanceList.get(m).delete();
        }
        this.instances.clear();
        this.instanceList.clear();
        Arrays.fill(this.slots, null);
    }

    /**
     * The instances of one model in this vehicle, in drawing order. Instances a frame does not need (a
     * fired missile, a part too small to see, the meshes of another level of detail) are parked with a
     * zero matrix, which draws nothing, and reused when the part comes back; creating and deleting
     * instances every time a vehicle crosses a level of detail would be wasted work.
     */
    private final class ModelInstances {

        private final ModelKeepers.Keeper keeper;
        private final List<TransformedInstance> list = new ArrayList<>();
        /** Instances below this index are placed; the rest are parked. */
        private int visible;
        /** Instances placed so far this frame. */
        private int used;
        /** The index of the instance last rewritten this frame, to count runs of rewrites. */
        private int lastWritten;

        ModelInstances(ModelKeepers.Keeper keeper) {
            this.keeper = keeper;
        }

        void beginFrame() {
            this.used = 0;
            this.lastWritten = -2;
        }

        /** Puts the next instance at {@code matrix}, rewriting it only if something changed. */
        void place(Matrix4fc matrix, int color, int light, boolean relight) {
            int index = this.used++;
            if (index == this.list.size()) {
                this.reserve(index + 1);
            }
            TransformedInstance instance = this.list.get(index);
            if (index >= this.visible) {
                // a parked instance: its colour and light may be stale
                relight = true;
                this.visible = index + 1;
            }
            boolean moved = !instance.pose.equals(matrix);
            if (moved) {
                instance.setTransform(matrix);
            }
            if (relight) {
                instance.colorArgb(color);
                instance.light(light);
            }
            if (moved || relight) {
                instance.setChanged();
                FlywheelVehicleStats.instanceWritten();
                if (index != this.lastWritten + 1) {
                    FlywheelVehicleStats.writeSpan();
                }
                this.lastWritten = index;
            }
        }

        /** Makes sure there are at least {@code count} instances, taking the missing ones parked. */
        void reserve(int count) {
            while (this.list.size() < count) {
                this.list.add(this.keeper.take());
            }
        }

        /** Parks the instances this frame did not place. */
        void parkUnused() {
            for (int i = this.used; i < this.visible; i++) {
                this.list.get(i).setZeroTransform().setChanged();
                FlywheelVehicleStats.instanceWritten();
            }
            this.visible = Math.min(this.visible, this.used);
        }

        /** Parks every instance and gives them back to the keeper. */
        void delete() {
            this.used = 0;
            this.parkUnused();
            this.keeper.giveBack(this.list);
            this.list.clear();
            ModelKeepers.release(this.keeper);
        }
    }
}
