package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.HullPose;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;

/**
 * The matrices an instancing backend draws a vehicle with: one hull matrix for the whole vehicle and
 * one matrix per part relative to the hull. Their product is the matrix the immediate renderer builds
 * on its pose stack for the same part, which the render equivalence check verifies. Nothing here
 * needs Flywheel, so the check runs without it.
 */
public final class VehicleInstanceTransforms {

    private VehicleInstanceTransforms() {
    }

    /** From the render origin to the airframe's body frame, written to {@code dest}. */
    public static Matrix4f hullMatrix(HullPose hull, Vec3i renderOrigin, PoseRecorder recorder, Matrix4f dest) {
        recorder.reset();
        recorder.translate(hull.x() - renderOrigin.getX(), hull.y() - renderOrigin.getY(),
                hull.z() - renderOrigin.getZ());
        VehicleModelPoser.applyHull(recorder, hull);
        return dest.set(recorder.current());
    }

    /** Records every part of the snapshot with its matrix relative to the body frame. */
    public static void partMatrices(VehicleRenderSnapshot snapshot, PoseRecorder recorder) {
        recorder.reset();
        VehicleModelPoser.pose(snapshot, recorder);
    }
}
