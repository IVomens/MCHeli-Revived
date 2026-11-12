package com.ivomens.mcheli.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The hot path of all model rendering: baked model data straight into the frame's buffer. */
final class BakedEmitter {

    /** Scratch normal for {@link #needsNormalization}; render thread only. */
    private static final Vector3f SCRATCH_NORMAL = new Vector3f();

    private BakedEmitter() {
    }

    /**
     * Emits baked model data ({@value LegacyGL#BAKED_STRIDE} floats per vertex: x, y, z, u, v, nx, ny, nz;
     * every face already stored as a quad) through the current pose, colour, light and texture.
     * <p>
     * This is the hot path of all model rendering, so the pose is read into locals once and each
     * vertex goes through the single-call {@link VertexConsumer#addVertex(float, float, float, int,
     * float, float, int, int, float, float, float)}, which buffer builders write in one go. The
     * result is the same as the per-attribute {@code addVertex(pose, ...)...setNormal(pose, ...)}
     * chain. A plain vanilla buffer builder is filled in one block by {@link EntityVertexWriter},
     * with the same bytes.
     */
    static void emit(RenderFrame current, float[] data, int firstVertex, int vertexCount) {
        RenderState state = current.state;
        VertexConsumer consumer = current.buffers.getBuffer(state.renderType());
        PoseStack.Pose pose = current.pose.last();
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        if (EntityVertexWriter.accepts(consumer)) {
            EntityVertexWriter.write(consumer, data, firstVertex, vertexCount, position, normal,
                    needsNormalization(pose), state.packedColor(), current.overlay, current.effectiveLight());
            return;
        }
        float p00 = position.m00(), p01 = position.m01(), p02 = position.m02();
        float p10 = position.m10(), p11 = position.m11(), p12 = position.m12();
        float p20 = position.m20(), p21 = position.m21(), p22 = position.m22();
        float p30 = position.m30(), p31 = position.m31(), p32 = position.m32();
        float n00 = normal.m00(), n01 = normal.m01(), n02 = normal.m02();
        float n10 = normal.m10(), n11 = normal.m11(), n12 = normal.m12();
        float n20 = normal.m20(), n21 = normal.m21(), n22 = normal.m22();
        boolean renormalize = needsNormalization(pose);
        int color = state.packedColor();
        int light = current.effectiveLight();
        int overlay = current.overlay;

        int end = (firstVertex + vertexCount) * LegacyGL.BAKED_STRIDE;
        for (int i = firstVertex * LegacyGL.BAKED_STRIDE; i < end; i += LegacyGL.BAKED_STRIDE) {
            float x = data[i], y = data[i + 1], z = data[i + 2];
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            float tnx = n00 * nx + n10 * ny + n20 * nz;
            float tny = n01 * nx + n11 * ny + n21 * nz;
            float tnz = n02 * nx + n12 * ny + n22 * nz;
            if (renormalize) {
                float scale = (float) (1.0 / Math.sqrt(tnx * tnx + tny * tny + tnz * tnz));
                tnx *= scale;
                tny *= scale;
                tnz *= scale;
            }
            consumer.addVertex(
                    p00 * x + p10 * y + p20 * z + p30,
                    p01 * x + p11 * y + p21 * z + p31,
                    p02 * x + p12 * y + p22 * z + p32,
                    color, data[i + 3], data[i + 4], overlay, light, tnx, tny, tnz);
        }
    }

    /**
     * Whether the pose stack would renormalize transformed normals. Its flag for that is not
     * public, but a trusted normal matrix is orthonormal and keeps a probe's length of 2, while an
     * untrusted one renormalizes it to 1.
     */
    private static boolean needsNormalization(PoseStack.Pose pose) {
        return pose.transformNormal(2.0F, 0.0F, 0.0F, SCRATCH_NORMAL).lengthSquared() < 2.0F;
    }
}
