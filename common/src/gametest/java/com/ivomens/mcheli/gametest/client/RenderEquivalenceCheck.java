package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.render.Angles;
import com.ivomens.mcheli.vehicle.client.render.ImmediateVehicleRenderer;
import com.ivomens.mcheli.vehicle.client.render.PoseRecorder;
import com.ivomens.mcheli.vehicle.client.render.VehicleInstanceTransforms;
import com.ivomens.mcheli.vehicle.client.render.VehicleModelPoser;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.GroundParts;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.HullPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.LandingGear;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.MountPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PartPoses;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PlaneParts;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Swing;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.TrackOffsets;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.WeaponPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot;
import com.ivomens.mcheli.vehicle.client.render.VehicleSnapshots;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the instancing backend places every part where the immediate renderer does. For each
 * vehicle and a set of poses (the real one, the hull turned and tilted, every moving part deflected)
 * it builds the matrices both ways: on a {@link LegacyGL} pose stack, as the immediate renderer draws,
 * and as Flywheel composes them (render origin, then hull times part matrix in the instance). Both must list the
 * same parts in the same order, with matrices equal to {@link #TOLERANCE} of their scale.
 * <p>
 * Flywheel itself is not needed: the visual builds its matrices with {@link VehicleInstanceTransforms}.
 */
final class RenderEquivalenceCheck {

    /** Largest allowed difference of a matrix element, relative to the element's size (at least 1). */
    static final float TOLERANCE = 1.0E-4F;
    private static final ResourceLocation ANY_TEXTURE = LegacyGL.WHITE;

    private RenderEquivalenceCheck() {
    }

    /** Compares every vehicle in every pose and logs one {@code [equivalence]} line; true when all match. */
    static boolean run(String label, Iterable<AircraftEntity> vehicles, Vec3 camera, float pixelsAtOneBlock) {
        int vehicleCount = 0;
        int poses = 0;
        int parts = 0;
        float worst = 0.0F;
        List<String> failures = new ArrayList<>();
        for (AircraftEntity vehicle : vehicles) {
            VehicleRenderSnapshot real = VehicleSnapshots.capture(vehicle,
                    new VehicleSnapshots.View(0.5F, camera, pixelsAtOneBlock), ANY_TEXTURE, 0);
            if (real == null || !(real.models().body() instanceof BakedModelBase)) {
                continue;
            }
            vehicleCount++;
            for (VehicleRenderSnapshot snapshot : List.of(real, turned(real), deflected(real), deflected(turned(real)))) {
                poses++;
                Comparison comparison = compare(snapshot, camera);
                parts += comparison.parts;
                worst = Math.max(worst, comparison.worst);
                if (comparison.failure != null && failures.size() < 20) {
                    failures.add(real.info().name() + ": " + comparison.failure);
                }
            }
        }
        boolean passed = failures.isEmpty() && vehicleCount > 0;
        ModLogger.get().info("[equivalence] {} {} vehicles={} poses={} parts={} worstRelativeError={}", label,
                passed ? "PASS" : "FAIL", vehicleCount, poses, parts, worst);
        for (String failure : failures) {
            ModLogger.get().info("[equivalence]   {}", failure);
        }
        return passed;
    }

    private record Comparison(int parts, float worst, String failure) {
    }

    private static Comparison compare(VehicleRenderSnapshot snapshot, Vec3 camera) {
        Recorder immediateParts = immediateParts(snapshot, camera);
        List<Matrix4f> immediate = immediateParts.matrices;
        List<PartMesh> immediateMeshes = immediateParts.meshes;

        Vec3i origin = new Vec3i((int) Math.floor(camera.x) & ~15, (int) Math.floor(camera.y) & ~15,
                (int) Math.floor(camera.z) & ~15);
        PoseRecorder recorder = new PoseRecorder();
        Matrix4f hull = VehicleInstanceTransforms.hullMatrix(snapshot.hull(), origin, recorder, new Matrix4f());
        VehicleInstanceTransforms.partMatrices(snapshot, recorder);
        if (recorder.size() != immediate.size()) {
            return new Comparison(0, 0.0F,
                    "part count " + recorder.size() + " instanced, " + immediate.size() + " immediate");
        }
        Matrix4f toCamera = new Matrix4f().translation((float) (origin.getX() - camera.x),
                (float) (origin.getY() - camera.y), (float) (origin.getZ() - camera.z));
        float worst = 0.0F;
        for (int i = 0; i < recorder.size(); i++) {
            if (recorder.mesh(i) != immediateMeshes.get(i)) {
                return new Comparison(i, worst, "part " + i + " differs in mesh");
            }
            Matrix4f instanced = new Matrix4f(toCamera).mul(hull).mul(recorder.matrix(i));
            float error = relativeError(instanced, immediate.get(i));
            worst = Math.max(worst, error);
            if (error > TOLERANCE) {
                return new Comparison(i, worst, "part " + i + " off by " + error);
            }
        }
        return new Comparison(recorder.size(), worst, null);
    }

    /** The parts and matrices the immediate renderer draws, starting from the entity's offset. */
    private static Recorder immediateParts(VehicleRenderSnapshot snapshot, Vec3 camera) {
        PoseStack pose = new PoseStack();
        HullPose hull = snapshot.hull();
        // as the entity render dispatcher places an entity
        pose.translate(hull.x() - camera.x, hull.y() - camera.y, hull.z() - camera.z);
        Recorder recorder = new Recorder();
        LegacyGL.begin(pose, null, 0);
        try {
            LegacyGL.pushMatrix();
            VehicleModelPoser.applyHull(recorder, hull);
            VehicleModelPoser.pose(snapshot, recorder);
            LegacyGL.popMatrix();
        } finally {
            LegacyGL.end();
        }
        return recorder;
    }

    private static float relativeError(Matrix4f a, Matrix4f b) {
        float worst = 0.0F;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float x = a.get(column, row);
                float y = b.get(column, row);
                worst = Math.max(worst, Math.abs(x - y) / Math.max(1.0F, Math.max(Math.abs(x), Math.abs(y))));
            }
        }
        return worst;
    }

    /** The immediate renderer's transforms, keeping the matrix at each draw instead of drawing. */
    private static final class Recorder extends ImmediateVehicleRenderer.LegacyGLPartSink {

        final List<Matrix4f> matrices = new ArrayList<>();
        final List<PartMesh> meshes = new ArrayList<>();

        @Override
        public void draw(PartMesh mesh) {
            this.matrices.add(new Matrix4f(LegacyGL.pose().last().pose()));
            this.meshes.add(mesh);
        }

        @Override
        public void draw(IModelCustom model) {
            if (model instanceof BakedModelBase baked) {
                this.draw(baked.wholeModel());
            }
        }
    }

    private static VehicleRenderSnapshot turned(VehicleRenderSnapshot s) {
        HullPose h = s.hull();
        return new VehicleRenderSnapshot(s.info(), s.models(), new HullPose(h.x() + 0.37, h.y() - 1.3, h.z() + 2.9,
                h.yaw() + 33.0F, h.pitch() - 12.0F, h.roll() + 21.0F, h.rolls()), s.texture(), s.tint(), s.light(),
                s.detail(), s.parts());
    }

    /** Every moving part pushed away from rest, so each transform path runs with non-zero angles. */
    private static VehicleRenderSnapshot deflected(VehicleRenderSnapshot s) {
        PartPoses p = s.parts();
        List<WeaponPose> weapons = new ArrayList<>();
        for (WeaponPose w : p.weapons()) {
            MountPose m = w.mount();
            MountPose mount = m == null ? null : new MountPose(m.defaultYaw(), m.turretRelativeYaw() + 7.0F, 0.4F,
                    m.barrel() + 45.0F, offset(m.childRecoil(), 0.3F));
            weapons.add(new WeaponPose(w.hidden(), w.loaded(), w.turretYaw() + 30.0F, w.yaw() + 25.0F,
                    w.pitch() - 15.0F, mount));
        }
        List<Angles> rotors = new ArrayList<>();
        for (Angles rotor : p.rotors()) {
            rotors.add(offset(rotor, 17.0F));
        }
        List<Swing> bays = new ArrayList<>();
        for (int i = 0; i < p.weaponBays().size(); i++) {
            bays.add(new Swing(50.0F, 0.6F));
        }
        PlaneParts plane = p.plane();
        GroundParts ground = p.ground();
        PartPoses parts = new PartPoses(p.intactEra(), weapons, offset(p.spinningParts(), 40.0F),
                p.hatch() == null ? null : new Swing(35.0F, 0.4F), p.canopy() == null ? null : new Swing(60.0F, 0.7F),
                Angles.of(25.0F, -30.0F), new TrackOffsets(0.3, 0.6), p.staticTracks(), 0.5F, 70.0F, 0.7F, 0.6F,
                20.0F, -10.0F, p.landingGear() == null ? null : new LandingGear(45.0F, 45.0F, 60.0F, 0.5F), bays,
                rotors, new PlaneParts(plane.nozzles(), plane.wings(), plane.rotors(), 30.0F, 40.0F, 50.0F),
                ground == null ? null : new GroundParts(ground.aimYaw() + 40.0F, ground.aimPitch() - 8.0F,
                        ground.barrel() + 33.0F, ground.hideFirstPerson(), offset(ground.recoil(), 0.25F),
                        ground.loaded()));
        return new VehicleRenderSnapshot(s.info(), s.models(), s.hull(), s.texture(), s.tint(), s.light(), s.detail(),
                parts);
    }

    private static Angles offset(Angles angles, float by) {
        float[] values = new float[angles.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = angles.get(i) + by;
        }
        return Angles.of(values);
    }
}
