package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.client.model.MeshDetail;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.vehicle.client.render.PoseRecorder;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.InstanceWriter;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.layout.Layout;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flywheel backend's bookkeeping against fakes of Flywheel's instancers: vehicles share one
 * instancer per part model, a steady frame creates and uploads nothing, a moving vehicle only
 * rewrites its matrices, and an orbiting camera (levels of detail, vehicles leaving and coming back,
 * render origin moves) makes nothing once everything has been seen. On Flywheel's instancing backend
 * each instancer is a draw call, every mesh made or freed re-uploads its whole mesh pool, and every
 * instance made or deleted re-uploads (part of) its instancer, so these are what keep it smooth.
 */
class VehicleInstancesTest {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mcheli", "test");
    /** Flywheel's own instance types need its implementation, which only the loader jars carry. */
    private static final InstanceType<TransformedInstance> TYPE = new FakeType();

    @Test
    void vehiclesShareOneInstancerPerModelAndSteadyFramesCreateNothing() {
        FakeContext context = new FakeContext();
        List<PartMesh> parts = List.of(quad(0), quad(1), quad(2));
        VehicleInstances first = new VehicleInstances(context, TYPE);
        VehicleInstances second = new VehicleInstances(context, TYPE);
        PoseRecorder recorder = new PoseRecorder();
        Matrix4f hullA = new Matrix4f().translation(1, 2, 3);
        Matrix4f hullB = new Matrix4f().translation(40, 2, 3);

        frame(first, recorder, parts, hullA);
        frame(second, recorder, parts, hullB);
        // three models, each with the instances of both vehicles and the one that keeps the mesh
        assertEquals(3, context.provider.instancers.size());
        for (FakeInstancer instancer : context.provider.instancers.values()) {
            assertEquals(3, instancer.alive());
        }

        FlywheelVehicleStats.Counts before = FlywheelVehicleStats.counts();
        long changedBefore = context.provider.changed;
        for (int i = 0; i < 10; i++) {
            frame(first, recorder, parts, hullA);
            frame(second, recorder, parts, hullB);
        }
        FlywheelVehicleStats.Counts steady = FlywheelVehicleStats.counts().since(before);
        assertTrue(steady.createsNothing(), steady::toString);
        assertEquals(0, steady.instanceWrites(), steady::toString);
        assertEquals(changedBefore, context.provider.changed, "instances marked changed in steady frames");

        // the hull moves: each part's matrix is rewritten, nothing is made
        FlywheelVehicleStats.Counts beforeMove = FlywheelVehicleStats.counts();
        frame(first, recorder, parts, new Matrix4f().translation(1.5F, 2, 3));
        FlywheelVehicleStats.Counts moved = FlywheelVehicleStats.counts().since(beforeMove);
        assertTrue(moved.createsNothing(), moved::toString);
        assertEquals(parts.size(), moved.instanceWrites(), moved::toString);
    }

    @Test
    void deletedVehiclesKeepTheMeshAndTheirInstancesUntilTheSweep() {
        FakeContext context = new FakeContext();
        List<PartMesh> parts = List.of(quad(3), quad(4));
        VehicleInstances vehicle = new VehicleInstances(context, TYPE);
        PoseRecorder recorder = new PoseRecorder();
        frame(vehicle, recorder, parts, new Matrix4f());

        FlywheelVehicleStats.Counts beforeDelete = FlywheelVehicleStats.counts();
        vehicle.delete();
        // the keepers and the parked instances remain, so Flywheel neither frees (and re-uploads) the
        // meshes nor moves the instancers' other instances down
        assertEquals(0, FlywheelVehicleStats.counts().since(beforeDelete).instancesDeleted());
        for (FakeInstancer instancer : context.provider.instancers.values()) {
            assertEquals(2, instancer.alive());
        }
        ModelKeepers.sweepNow(true, System.nanoTime());
        for (FakeInstancer instancer : context.provider.instancers.values()) {
            assertEquals(2, instancer.alive());
        }
        // a vehicle of the type comes back: no new mesh, model, keeper or instance
        FlywheelVehicleStats.Counts before = FlywheelVehicleStats.counts();
        VehicleInstances back = new VehicleInstances(context, TYPE);
        frame(back, recorder, parts, new Matrix4f());
        FlywheelVehicleStats.Counts returned = FlywheelVehicleStats.counts().since(before);
        assertEquals(0, returned.meshesCreated() + returned.modelsCreated());
        assertEquals(0, returned.instancesCreated() + returned.instancesDeleted(), returned::toString);

        // spares nobody took go after a while, the keepers after a longer while
        back.delete();
        ModelKeepers.sweepNow(true, System.nanoTime() + ModelKeepers.POOL_IDLE_NANOS);
        for (FakeInstancer instancer : context.provider.instancers.values()) {
            assertEquals(1, instancer.alive());
        }
        ModelKeepers.sweepNow(false, System.nanoTime());
        for (FakeInstancer instancer : context.provider.instancers.values()) {
            assertEquals(0, instancer.alive());
        }
    }

    @Test
    void newModelsWaitForTheNextAdmissionTogether() throws InterruptedException {
        FakeContext context = new FakeContext();
        PoseRecorder recorder = new PoseRecorder();
        ModelKeepers.openAdmission();
        frame(new VehicleInstances(context, TYPE), recorder, List.of(quad(20)), new Matrix4f());
        // the window has closed: a vehicle of a new type is left to the immediate renderer, makes nothing
        Thread.sleep(80);
        VehicleInstances late = new VehicleInstances(context, TYPE);
        FlywheelVehicleStats.Counts before = FlywheelVehicleStats.counts();
        recorder.reset();
        recorder.draw(quad(21));
        assertTrue(!late.update(recorder, new Matrix4f(), MeshDetail.FULL, TEXTURE, 0xFFFFFFFF, 0xF000F0));
        FlywheelVehicleStats.Counts waited = FlywheelVehicleStats.counts().since(before);
        assertEquals(0, waited.keepersCreated() + waited.instancesCreated(), waited::toString);
        assertEquals(1, context.provider.instancers.size());
        // the next admission lets it in
        ModelKeepers.openAdmission();
        assertTrue(late.update(recorder, new Matrix4f(), MeshDetail.FULL, TEXTURE, 0xFFFFFFFF, 0xF000F0));
        assertEquals(2, context.provider.instancers.size());
    }

    @Test
    void crossingTheFarLevelOnlyMovesInstances() {
        FakeContext context = new FakeContext();
        List<PartMesh> parts = List.of(quad(5, true), quad(6, true));
        VehicleInstances vehicle = new VehicleInstances(context, TYPE);
        PoseRecorder recorder = new PoseRecorder();
        frame(vehicle, recorder, parts, new Matrix4f(), MeshDetail.FULL);
        // the far level's models only once a vehicle needs them: they would be a draw call each
        assertEquals(2, context.provider.instancers.size());
        frame(vehicle, recorder, parts, new Matrix4f(), FlywheelModels.farLevel());
        frame(vehicle, recorder, parts, new Matrix4f(), MeshDetail.FULL);
        assertEquals(4, context.provider.instancers.size());

        // from then on the vehicle keeps both levels' instances
        FlywheelVehicleStats.Counts before = FlywheelVehicleStats.counts();
        frame(vehicle, recorder, parts, new Matrix4f(), FlywheelModels.farLevel());
        frame(vehicle, recorder, parts, new Matrix4f(), MeshDetail.FULL);
        FlywheelVehicleStats.Counts crossed = FlywheelVehicleStats.counts().since(before);
        assertTrue(crossed.createsNothing(), crossed::toString);
        // each crossing places one level's instances and parks the other's
        assertEquals(4 * parts.size(), crossed.instanceWrites(), crossed::toString);
    }

    /**
     * A camera circling a steady scene: vehicles cross the far level, leave the tracking range and come
     * back, and Flywheel moves its render origin twice per turn, clearing every instancer and recreating
     * every visual. Once the vehicles hold the instances they need (the third turn: a vehicle keeps
     * both levels' instances, so the first turns still lend and take), nothing is made except the
     * instances Flywheel cleared, and no instancer is ever left empty, which would free its mesh and
     * re-upload the whole pool.
     */
    @Test
    void anOrbitingCameraOverASteadySceneCreatesNothing() {
        FakeContext context = new FakeContext();
        List<List<PartMesh>> types = List.of(List.of(quad(10, true), quad(11, true)),
                List.of(quad(12, true), quad(13, false), quad(14, true)), List.of(quad(15, true)));
        int vehicles = 12;
        VehicleInstances[] scene = new VehicleInstances[vehicles];
        PoseRecorder recorder = new PoseRecorder();
        for (int frame = 0; frame < 3 * ORBIT_FRAMES; frame++) {
            FlywheelVehicleStats.Counts before = FlywheelVehicleStats.counts();
            boolean originMoved = frame % (ORBIT_FRAMES / 2) == ORBIT_FRAMES / 4;
            if (originMoved) {
                // Flywheel clears every instance, then recreates every visual, which draws at once
                context.moveOrigin();
                for (int v = 0; v < vehicles; v++) {
                    if (scene[v] != null) {
                        scene[v].delete();
                        scene[v] = new VehicleInstances(context, TYPE);
                        orbitFrame(scene[v], recorder, types.get(v % types.size()), v, frame);
                    }
                }
            } else {
                for (int v = 0; v < vehicles; v++) {
                    boolean inRange = (frame + v * 7) % ORBIT_FRAMES < ORBIT_FRAMES * 3 / 4;
                    if (inRange && scene[v] == null) {
                        scene[v] = new VehicleInstances(context, TYPE);
                    } else if (!inRange && scene[v] != null) {
                        scene[v].delete();
                        scene[v] = null;
                    }
                    if (scene[v] != null) {
                        orbitFrame(scene[v], recorder, types.get(v % types.size()), v, frame);
                    }
                }
            }
            ModelKeepers.sweep(true);
            for (FakeInstancer instancer : context.provider.instancers.values()) {
                assertTrue(instancer.alive() > 0, "an instancer was left empty in frame " + frame);
            }
            FlywheelVehicleStats.Counts made = FlywheelVehicleStats.counts().since(before);
            if (frame >= 2 * ORBIT_FRAMES) {
                assertEquals(0, made.meshesCreated() + made.modelsCreated(), made::toString);
                if (!originMoved) {
                    assertTrue(made.createsNothing(), "steady orbit frame " + frame + ": " + made);
                }
            }
        }
    }

    private static final int ORBIT_FRAMES = 40;

    /** A vehicle seen from the orbit: at the far level for half of the turn. */
    private static void orbitFrame(VehicleInstances vehicle, PoseRecorder recorder, List<PartMesh> parts, int index,
                                   int frame) {
        int level = (frame + index * 3) % ORBIT_FRAMES < ORBIT_FRAMES / 2 ? MeshDetail.FULL : FlywheelModels.farLevel();
        frame(vehicle, recorder, parts, new Matrix4f().translation(index * 10, 0, 0), level);
    }

    private static void frame(VehicleInstances vehicle, PoseRecorder recorder, List<PartMesh> parts, Matrix4f hull) {
        frame(vehicle, recorder, parts, hull, MeshDetail.FULL);
    }

    private static void frame(VehicleInstances vehicle, PoseRecorder recorder, List<PartMesh> parts, Matrix4f hull,
                              int detailLevel) {
        recorder.reset();
        for (int i = 0; i < parts.size(); i++) {
            recorder.pushPose();
            recorder.translate(i, 0, 0);
            recorder.draw(parts.get(i));
            recorder.popPose();
        }
        if (!vehicle.update(recorder, hull, detailLevel, TEXTURE, 0xFFFFFFFF, 0xF000F0)) {
            // a model that is not resident yet waits for the next admission, which a test does not
            ModelKeepers.openAdmission();
            assertTrue(vehicle.update(recorder, hull, detailLevel, TEXTURE, 0xFFFFFFFF, 0xF000F0));
        }
    }

    /** A part of one quad, different for every {@code seed}. */
    private static PartMesh quad(int seed) {
        return quad(seed, false);
    }

    /** The same, with other vertices at the far level if {@code farDiffers}. */
    private static PartMesh quad(int seed, boolean farDiffers) {
        float[] vertices = quadVertices(seed);
        float[] far = farDiffers ? quadVertices(seed + 0.5F) : vertices;
        return new PartMesh() {
            @Override
            public void render() {
            }

            @Override
            public float[] vertices(int detailLevel) {
                return detailLevel == FlywheelModels.farLevel() ? far : vertices;
            }
        };
    }

    private static float[] quadVertices(float seed) {
        float[] vertices = new float[4 * LegacyGL.BAKED_STRIDE];
        for (int v = 0; v < 4; v++) {
            int i = v * LegacyGL.BAKED_STRIDE;
            vertices[i] = seed + (v & 1);
            vertices[i + 1] = v >> 1;
            vertices[i + 6] = 1.0F;
        }
        return vertices;
    }

    private static final class FakeType implements InstanceType<TransformedInstance> {

        @Override
        public TransformedInstance create(InstanceHandle handle) {
            return new TransformedInstance(this, handle);
        }

        @Override
        public Layout layout() {
            throw new AssertionError("not used");
        }

        @Override
        public InstanceWriter<TransformedInstance> writer() {
            throw new AssertionError("not used");
        }

        @Override
        public ResourceLocation vertexShader() {
            throw new AssertionError("not used");
        }

        @Override
        public ResourceLocation cullShader() {
            throw new AssertionError("not used");
        }
    }

    private static final class FakeContext implements VisualizationContext {

        final FakeProvider provider = new FakeProvider();
        private Vec3i origin = Vec3i.ZERO;

        /** As Flywheel does when the camera went too far: a new origin, and every instance cleared. */
        void moveOrigin() {
            this.origin = this.origin.offset(256, 0, 0);
            for (FakeInstancer instancer : this.provider.instancers.values()) {
                instancer.clear();
            }
        }

        @Override
        public InstancerProvider instancerProvider() {
            return this.provider;
        }

        @Override
        public Vec3i renderOrigin() {
            return this.origin;
        }

        @Override
        public VisualEmbedding createEmbedding(Vec3i renderOrigin) {
            throw new AssertionError("vehicles draw without embeddings");
        }
    }

    private static final class FakeProvider implements InstancerProvider {

        final Map<Model, FakeInstancer> instancers = new HashMap<>();
        long changed;

        @Override
        @SuppressWarnings("unchecked")
        public synchronized <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
            return (Instancer<I>) this.instancers.computeIfAbsent(model, key -> new FakeInstancer(type, this));
        }
    }

    private static final class FakeInstancer implements Instancer<Instance> {

        private final InstanceType<?> type;
        private final FakeProvider provider;
        private final List<FakeHandle> handles = new ArrayList<>();

        FakeInstancer(InstanceType<?> type, FakeProvider provider) {
            this.type = type;
            this.provider = provider;
        }

        @Override
        public Instance createInstance() {
            FakeHandle handle = new FakeHandle(this.provider);
            this.handles.add(handle);
            return this.type.create(handle);
        }

        @Override
        public void stealInstance(Instance instance) {
            throw new AssertionError("not used");
        }

        int alive() {
            return (int) this.handles.stream().filter(handle -> !handle.deleted).count();
        }

        /** Flywheel's clear: every instance gone, and their handles no longer do anything. */
        void clear() {
            for (FakeHandle handle : this.handles) {
                handle.deleted = true;
            }
            this.handles.clear();
        }
    }

    private static final class FakeHandle implements InstanceHandle {

        private final FakeProvider provider;
        boolean deleted;

        FakeHandle(FakeProvider provider) {
            this.provider = provider;
        }

        @Override
        public void setChanged() {
            this.provider.changed++;
        }

        @Override
        public void setDeleted() {
            this.deleted = true;
        }

        @Override
        public void setVisible(boolean visible) {
        }

        @Override
        public boolean isVisible() {
            return true;
        }
    }
}
