package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.client.render.LegacyGL;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PartSink} that keeps every drawn part with its matrix instead of drawing it, for
 * instancing. It starts from the identity, so the matrices are relative to wherever posing began
 * (usually the airframe). The transforms are the same operations {@link LegacyGL} applies to its
 * pose stack, so a part's matrix matches the immediate renderer's.
 * <p>
 * One recorder is reused frame after frame; it allocates only when a vehicle has more parts than
 * any frame before.
 */
public final class PoseRecorder implements PartSink {

    private static final int MAX_DEPTH = 64;

    private final Matrix4fStack stack = new Matrix4fStack(MAX_DEPTH);
    private final Quaternionf rotation = new Quaternionf();
    private final List<Matrix4f> matrices = new ArrayList<>();
    private final List<PartMesh> meshes = new ArrayList<>();
    private int count;

    /** Forgets the recorded parts and goes back to the identity. */
    public void reset() {
        this.stack.clear();
        this.count = 0;
    }

    /** The number of parts recorded since the last {@link #reset()}. */
    public int size() {
        return this.count;
    }

    public PartMesh mesh(int index) {
        return this.meshes.get(index);
    }

    /** The part's matrix; valid until the next {@link #reset()}. */
    public Matrix4fc matrix(int index) {
        return this.matrices.get(index);
    }

    /** The current transform. */
    public Matrix4fc current() {
        return this.stack;
    }

    @Override
    public void pushPose() {
        this.stack.pushMatrix();
    }

    @Override
    public void popPose() {
        this.stack.popMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        this.stack.translate((float) x, (float) y, (float) z);
    }

    @Override
    public void rotate(float degrees, float axisX, float axisY, float axisZ) {
        if (degrees != 0.0F) {
            this.stack.rotate(LegacyGL.rotation(degrees, axisX, axisY, axisZ, this.rotation));
        }
    }

    @Override
    public void rotateAround(float degrees, float axisX, float axisY, float axisZ, double pivotX, double pivotY,
                             double pivotZ) {
        if (degrees != 0.0F) {
            this.stack.rotateAround(LegacyGL.rotation(degrees, axisX, axisY, axisZ, this.rotation),
                    (float) pivotX, (float) pivotY, (float) pivotZ);
        }
    }

    @Override
    public void draw(PartMesh mesh) {
        if (this.count == this.matrices.size()) {
            this.matrices.add(new Matrix4f());
            this.meshes.add(mesh);
        } else {
            this.meshes.set(this.count, mesh);
        }
        this.matrices.get(this.count).set(this.stack);
        this.count++;
    }

    /** A whole model is drawn as one mesh; a placeholder that is still loading draws nothing. */
    @Override
    public void draw(IModelCustom model) {
        if (model instanceof BakedModelBase baked) {
            this.draw(baked.wholeModel());
        }
    }
}
