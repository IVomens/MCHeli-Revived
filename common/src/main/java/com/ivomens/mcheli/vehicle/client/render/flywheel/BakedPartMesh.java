package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.client.render.LegacyGL;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.math.DataPacker;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/**
 * The vertices of one baked part at one level of detail, as a Flywheel mesh. Flywheel uploads it once
 * and draws every instance of the part from the same buffer. Colour and light come from the instance.
 */
final class BakedPartMesh implements QuadMesh {

    private static final int STRIDE = LegacyGL.BAKED_STRIDE;
    /** Opaque white in every byte order. */
    private static final int WHITE = 0xFFFFFFFF;

    private final float[] vertices;
    private final Vector4f boundingSphere;

    BakedPartMesh(float[] vertices) {
        this.vertices = vertices;
        this.boundingSphere = boundingSphere(vertices);
    }

    boolean isEmpty() {
        return this.vertices.length == 0;
    }

    /** Whether the mesh holds exactly these vertices. */
    boolean hasVertices(float[] other) {
        return this.vertices == other || Arrays.equals(this.vertices, other);
    }

    @Override
    public int vertexCount() {
        return this.vertices.length / STRIDE;
    }

    @Override
    public void write(MutableVertexList list) {
        FlywheelVehicleStats.meshWritten(this.vertexCount());
        if (list instanceof FullVertexView view) {
            this.writeFull(view.ptr());
            return;
        }
        int count = this.vertexCount();
        for (int v = 0; v < count; v++) {
            int i = v * STRIDE;
            list.x(v, this.vertices[i]);
            list.y(v, this.vertices[i + 1]);
            list.z(v, this.vertices[i + 2]);
            list.r(v, 1.0F);
            list.g(v, 1.0F);
            list.b(v, 1.0F);
            list.a(v, 1.0F);
            list.u(v, this.vertices[i + 3]);
            list.v(v, this.vertices[i + 4]);
            list.overlay(v, OverlayTexture.NO_OVERLAY);
            list.light(v, 0);
            list.normalX(v, this.vertices[i + 5]);
            list.normalY(v, this.vertices[i + 6]);
            list.normalZ(v, this.vertices[i + 7]);
        }
    }

    /**
     * The same vertices written straight into the layout of {@link FullVertexView}, the one Flywheel's
     * backends upload meshes with. Flywheel uploads its whole mesh pool whenever a mesh comes or goes,
     * and vehicle meshes are large, so this path matters.
     */
    private void writeFull(long base) {
        int count = this.vertexCount();
        for (int v = 0; v < count; v++) {
            int i = v * STRIDE;
            long p = base + v * FullVertexView.STRIDE;
            MemoryUtil.memPutFloat(p, this.vertices[i]);
            MemoryUtil.memPutFloat(p + 4, this.vertices[i + 1]);
            MemoryUtil.memPutFloat(p + 8, this.vertices[i + 2]);
            MemoryUtil.memPutInt(p + 12, WHITE);
            MemoryUtil.memPutFloat(p + 16, this.vertices[i + 3]);
            MemoryUtil.memPutFloat(p + 20, this.vertices[i + 4]);
            MemoryUtil.memPutInt(p + 24, OverlayTexture.NO_OVERLAY);
            MemoryUtil.memPutInt(p + 28, 0);
            MemoryUtil.memPutByte(p + 32, DataPacker.packNormI8(this.vertices[i + 5]));
            MemoryUtil.memPutByte(p + 33, DataPacker.packNormI8(this.vertices[i + 6]));
            MemoryUtil.memPutByte(p + 34, DataPacker.packNormI8(this.vertices[i + 7]));
        }
    }

    @Override
    public Vector4fc boundingSphere() {
        return this.boundingSphere;
    }

    /** The sphere around the bounding box: (centre x, y, z, radius). */
    private static Vector4f boundingSphere(float[] vertices) {
        if (vertices.length == 0) {
            return new Vector4f();
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i += STRIDE) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        float sizeX = maxX - minX, sizeY = maxY - minY, sizeZ = maxZ - minZ;
        return new Vector4f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                0.5F * (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ));
    }
}
