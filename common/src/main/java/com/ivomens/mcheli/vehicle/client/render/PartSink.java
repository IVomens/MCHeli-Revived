package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.PartMesh;

/**
 * Where {@link VehicleModelPoser} sends a vehicle's parts: a transform stack with the fixed-function
 * vocabulary the vehicle definitions are written for, and the meshes to draw at the current
 * transform. The immediate renderer draws them into the {@code LegacyGL} frame; {@link PoseRecorder}
 * keeps each part's matrix for instancing.
 * <p>
 * Rotations take degrees about an axis, like {@code glRotatef}; an axis-aligned axis only counts by
 * its sign, and a zero angle does nothing.
 */
public interface PartSink {

    void pushPose();

    void popPose();

    void translate(double x, double y, double z);

    void rotate(float degrees, float axisX, float axisY, float axisZ);

    /** Rotates about an axis through {@code (pivotX, pivotY, pivotZ)}. */
    void rotateAround(float degrees, float axisX, float axisY, float axisZ, double pivotX, double pivotY,
                      double pivotZ);

    /** Draws a baked part or mesh at the current transform. */
    void draw(PartMesh mesh);

    /** Draws a whole model at the current transform; a model still loading draws nothing. */
    void draw(IModelCustom model);
}
