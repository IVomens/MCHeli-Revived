package com.ivomens.mcheli.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteOrder;

/**
 * Writes baked model data into a vanilla {@link BufferBuilder} as one block: the space for every
 * vertex is reserved at once and filled with plain memory writes, instead of one
 * {@code addVertex} call (with its per-vertex bookkeeping) per vertex.
 * <p>
 * The bytes are the ones {@link BufferBuilder#addVertex(float, float, float, int, float, float, int,
 * int, float, float, float)} writes for the {@link DefaultVertexFormat#NEW_ENTITY} format, computed
 * the same way, so the image is the same. The only difference is the unused padding byte after the
 * normal, which vanilla leaves as it was and this writes as zero.
 * <p>
 * It only takes over a builder it fully understands: exactly {@code BufferBuilder} (not a subclass
 * or a wrapper such as an outline or decal consumer), building quads in the {@code NEW_ENTITY}
 * format with no vertex left half-written, on a little-endian machine. Shader mods that extend the
 * entity format (Iris) give the builder another format, so their buffers keep the per-vertex path
 * and get every attribute they add. {@code -Dmcheli.render.bulkVertices=false} switches it off, and
 * {@code -Dmcheli.render.meshBlocks=false} only the cache of finished blocks.
 */
final class EntityVertexWriter {

    private static final boolean ENABLED = !"false".equals(System.getProperty("mcheli.render.bulkVertices"))
            && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final boolean CACHE_ENABLED = !"false".equals(System.getProperty("mcheli.render.meshBlocks"));
    private static final int VERTEX_SIZE = VertexTransform.VERTEX_SIZE;
    /** Render thread only, like everything that draws through {@link LegacyGL}. */
    private static final VertexTransform TRANSFORM = new VertexTransform();
    private static final MeshBlockCache BLOCKS = new MeshBlockCache();

    private EntityVertexWriter() {
    }

    /** Whether {@link #write} can fill this consumer. */
    static boolean accepts(VertexConsumer consumer) {
        return ENABLED
                && consumer.getClass() == BufferBuilder.class
                && accepts((BufferBuilder) consumer);
    }

    private static boolean accepts(BufferBuilder builder) {
        return builder.building
                && builder.format == DefaultVertexFormat.NEW_ENTITY
                && builder.mode == VertexFormat.Mode.QUADS
                && (builder.vertices == 0 || builder.elementsToFill == 0);
    }

    /** Reserves {@code count} vertices at the end of the builder and returns where the first one goes. */
    private static long reserve(BufferBuilder builder, int count) {
        long first = builder.buffer.reserve(count * VERTEX_SIZE);
        builder.vertices += count;
        builder.vertexPointer = first + (long) (count - 1) * VERTEX_SIZE;
        builder.elementsToFill = 0;
        return first;
    }

    /**
     * Writes baked vertices through a pose, as {@code BakedEmitter} would one by one: from the
     * {@link MeshBlockCache} when the mesh was drawn the same way before, else computed. The
     * consumer must have passed {@link #accepts}.
     */
    static void write(VertexConsumer consumer, float[] data, int firstVertex, int vertexCount, Matrix4f position,
                      Matrix3f normal, boolean renormalize, int argb, int overlay, int light) {
        long pointer = reserve((BufferBuilder) consumer, vertexCount);
        VertexTransform transform = TRANSFORM;
        transform.set(position, normal, renormalize, argb, overlay, light);
        if (vertexCount >= MeshBlockCache.MIN_VERTICES && CACHE_ENABLED
                && BLOCKS.write(pointer, data, firstVertex, vertexCount, transform)) {
            return;
        }
        transform.fill(pointer, data, firstVertex, vertexCount, null);
    }

    /** Frees the cached blocks, for when the models they were made of are gone. */
    static void clearCache() {
        BLOCKS.clear();
    }
}
