package com.ivomens.mcheli.client.render;

import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The block writer fills a buffer with exactly the bytes the per-vertex path writes. */
class EntityVertexWriterTest {

    private static final int VERTICES = 4 * 500;
    private static final int VERTEX_SIZE = 36;
    private static final int PADDING = 35;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    /** Wraps nothing, but is not a plain {@code BufferBuilder}, so the emitter writes it vertex by vertex. */
    private static final class PerVertexBuilder extends BufferBuilder {
        PerVertexBuilder(ByteBufferBuilder buffer) {
            super(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        }
    }

    @Test
    void onlyPlainEntityQuadBuildersAreWrittenInBlocks() {
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(1024)) {
            assertTrue(EntityVertexWriter.accepts(
                    new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)));
        }
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(1024)) {
            assertFalse(EntityVertexWriter.accepts(new PerVertexBuilder(buffer)));
        }
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(1024)) {
            assertFalse(EntityVertexWriter.accepts(
                    new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)));
        }
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(1024)) {
            BufferBuilder halfWritten = new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            halfWritten.addVertex(0.0F, 0.0F, 0.0F);
            assertFalse(EntityVertexWriter.accepts(halfWritten));
        }
    }

    @Test
    void rotatedTranslatedPoseWritesTheSameBytes() {
        assertSameBytes(pose -> {
            pose.translate(-12.345, 3.25, 101.5);
            pose.mulPose(new org.joml.Quaternionf().rotationXYZ(0.3F, -1.2F, 2.1F));
            pose.translate(0.5, -0.25, 1.0);
        }, 0xFFBFBFBF, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    @Test
    void scaledPoseRenormalizesTheSameWay() {
        assertSameBytes(pose -> {
            pose.translate(7.0, -2.0, 0.125);
            pose.mulPose(new org.joml.Quaternionf().rotationY(0.7F));
            pose.scale(1.5F, 0.5F, 2.0F);
        }, 0x80FF2040, 0x00F000A0, OverlayTexture.pack(3, 10));
    }

    @Test
    void mirroredPoseWritesTheSameBytes() {
        assertSameBytes(pose -> pose.scale(-1.0F, 1.0F, 1.0F), 0xFFFFFFFF, 0, OverlayTexture.NO_OVERLAY);
    }

    /**
     * The second draw of a mesh in the same way makes its block, later ones copy it, and a draw that
     * only moved (the camera did) adds the new translation to the kept positions.
     */
    @Test
    void repeatedDrawsFromCachedBlocksWriteTheSameBytes() {
        float[] data = randomMesh(new Random(7));
        Consumer<PoseStack> turned = pose -> pose.mulPose(new org.joml.Quaternionf().rotationXYZ(-0.4F, 2.5F, 0.2F));
        for (int draw = 0; draw < 4; draw++) {
            assertSameBytes(data, turned, 0xFFC0C0C0, 0x00A000B0, OverlayTexture.NO_OVERLAY);
        }
        for (double[] at : new double[][]{{3.5, -1.0, 250.25}, {-0.0, 0.0, -0.0}, {1.0E-7, 64.0, -1024.5}}) {
            Consumer<PoseStack> moved = pose -> {
                pose.translate(at[0], at[1], at[2]);
                turned.accept(pose);
            };
            for (int draw = 0; draw < 2; draw++) {
                assertSameBytes(data, moved, 0xFFC0C0C0, 0x00A000B0, OverlayTexture.NO_OVERLAY);
            }
        }
        // the same pose in another colour is another block
        assertSameBytes(data, turned, 0xFF808080, 0x00A000B0, OverlayTexture.NO_OVERLAY);
        assertSameBytes(data, turned, 0xFF808080, 0x00A000B0, OverlayTexture.NO_OVERLAY);
        assertSameBytes(data, turned, 0xFF808080, 0x00A000B0, OverlayTexture.NO_OVERLAY);
        LegacyGL.releaseCachedMeshes();
        assertSameBytes(data, turned, 0xFFC0C0C0, 0x00A000B0, OverlayTexture.NO_OVERLAY);
    }

    private static void assertSameBytes(Consumer<PoseStack> posing, int argb, int light, int overlay) {
        assertSameBytes(randomMesh(new Random(argb ^ light)), posing, argb, light, overlay);
    }

    private static void assertSameBytes(float[] data, Consumer<PoseStack> posing, int argb, int light, int overlay) {
        byte[] perVertex = emit(data, posing, argb, light, overlay, true);
        byte[] block = emit(data, posing, argb, light, overlay, false);
        assertEquals(perVertex.length, block.length);
        assertEquals(VERTICES * VERTEX_SIZE, block.length);
        for (int i = 0; i < block.length; i++) {
            if (i % VERTEX_SIZE != PADDING && perVertex[i] != block[i]) {
                throw new AssertionError("vertex " + i / VERTEX_SIZE + " byte " + i % VERTEX_SIZE + " differs: "
                        + perVertex[i] + " per vertex, " + block[i] + " in a block");
            }
        }
    }

    private static byte[] emit(float[] data, Consumer<PoseStack> posing, int argb, int light, int overlay,
                               boolean perVertex) {
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(VERTICES * VERTEX_SIZE)) {
            BufferBuilder builder = perVertex ? new PerVertexBuilder(buffer)
                    : new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            PoseStack pose = new PoseStack();
            posing.accept(pose);
            LegacyGL.begin(pose, type -> builder, light, overlay);
            try {
                LegacyGL.color(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F,
                        ((argb >>> 24) & 0xFF) / 255.0F);
                // two calls, so the second block follows vertices already in the builder
                LegacyGL.emitBaked(data, 0, VERTICES / 2);
                LegacyGL.emitBaked(data, VERTICES / 2, VERTICES / 2);
            } finally {
                LegacyGL.end();
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer vertices = mesh.vertexBuffer();
                byte[] bytes = new byte[vertices.remaining()];
                vertices.get(bytes);
                return bytes;
            }
        }
    }

    /** Positions, texture coordinates and normals of every size, including normals past unit length. */
    private static float[] randomMesh(Random random) {
        float[] data = new float[VERTICES * LegacyGL.BAKED_STRIDE];
        for (int i = 0; i < data.length; i += LegacyGL.BAKED_STRIDE) {
            data[i] = (random.nextFloat() - 0.5F) * 40.0F;
            data[i + 1] = (random.nextFloat() - 0.5F) * 10.0F;
            data[i + 2] = (random.nextFloat() - 0.5F) * 40.0F;
            data[i + 3] = random.nextFloat();
            data[i + 4] = random.nextFloat();
            data[i + 5] = (random.nextFloat() - 0.5F) * 2.4F;
            data[i + 6] = (random.nextFloat() - 0.5F) * 2.4F;
            data[i + 7] = (random.nextFloat() - 0.5F) * 2.4F;
        }
        return data;
    }
}
