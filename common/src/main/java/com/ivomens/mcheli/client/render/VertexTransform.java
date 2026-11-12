package com.ivomens.mcheli.client.render;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;

/**
 * Everything that turns baked model data into {@code NEW_ENTITY} vertex bytes: the pose's position
 * and normal matrices, whether normals are renormalized, and the colour, overlay and light every
 * vertex gets. {@link #fill} writes the bytes {@code BufferBuilder.addVertex} would write for the
 * {@code BakedEmitter} call, computed the same way (see {@link EntityVertexWriter}).
 * <p>
 * The {@linkplain #linearKey() linear part} (all of it but the translation) is what
 * {@link MeshBlockCache} keys on: a vertex position is {@code ((m00 x + m10 y) + m20 z) + m30}, so
 * with the same linear part, the sum before the translation is the same, and only the last addition
 * has to be redone when the translation changes.
 */
final class VertexTransform {

    /** Bytes of one {@code NEW_ENTITY} vertex. */
    static final int VERTEX_SIZE = 36;
    private static final int STRIDE = LegacyGL.BAKED_STRIDE;
    /** Linear position part, normal matrix, renormalize flag, colour, overlay and light, as ints. */
    static final int KEY_INTS = 9 + 9 + 1 + 1 + 2;

    private float p00, p01, p02, p10, p11, p12, p20, p21, p22;
    private float p30, p31, p32;
    private float n00, n01, n02, n10, n11, n12, n20, n21, n22;
    private boolean renormalize;
    private int abgr;
    private long overlayAndLight;

    void set(Matrix4f position, Matrix3f normal, boolean renormalize, int argb, int overlay, int light) {
        this.p00 = position.m00();
        this.p01 = position.m01();
        this.p02 = position.m02();
        this.p10 = position.m10();
        this.p11 = position.m11();
        this.p12 = position.m12();
        this.p20 = position.m20();
        this.p21 = position.m21();
        this.p22 = position.m22();
        this.p30 = position.m30();
        this.p31 = position.m31();
        this.p32 = position.m32();
        this.n00 = normal.m00();
        this.n01 = normal.m01();
        this.n02 = normal.m02();
        this.n10 = normal.m10();
        this.n11 = normal.m11();
        this.n12 = normal.m12();
        this.n20 = normal.m20();
        this.n21 = normal.m21();
        this.n22 = normal.m22();
        this.renormalize = renormalize;
        this.abgr = FastColor.ABGR32.fromArgb32(argb);
        // overlay then light, two little-endian ints
        this.overlayAndLight = (overlay & 0xFFFFFFFFL) | ((long) light << 32);
    }

    float translationX() {
        return this.p30;
    }

    float translationY() {
        return this.p31;
    }

    float translationZ() {
        return this.p32;
    }

    /**
     * Writes everything but the translation to {@code key} ({@link #KEY_INTS} ints), bit for bit, so
     * 0 and -0 differ, as their sums can.
     */
    void linearKey(int[] key) {
        key[0] = Float.floatToRawIntBits(this.p00);
        key[1] = Float.floatToRawIntBits(this.p01);
        key[2] = Float.floatToRawIntBits(this.p02);
        key[3] = Float.floatToRawIntBits(this.p10);
        key[4] = Float.floatToRawIntBits(this.p11);
        key[5] = Float.floatToRawIntBits(this.p12);
        key[6] = Float.floatToRawIntBits(this.p20);
        key[7] = Float.floatToRawIntBits(this.p21);
        key[8] = Float.floatToRawIntBits(this.p22);
        key[9] = Float.floatToRawIntBits(this.n00);
        key[10] = Float.floatToRawIntBits(this.n01);
        key[11] = Float.floatToRawIntBits(this.n02);
        key[12] = Float.floatToRawIntBits(this.n10);
        key[13] = Float.floatToRawIntBits(this.n11);
        key[14] = Float.floatToRawIntBits(this.n12);
        key[15] = Float.floatToRawIntBits(this.n20);
        key[16] = Float.floatToRawIntBits(this.n21);
        key[17] = Float.floatToRawIntBits(this.n22);
        key[18] = this.renormalize ? 1 : 0;
        key[19] = this.abgr;
        key[20] = (int) this.overlayAndLight;
        key[21] = (int) (this.overlayAndLight >>> 32);
    }

    /**
     * Writes {@code count} vertices of baked {@code data} from {@code first} to native memory at
     * {@code pointer}. With {@code untranslated}, also keeps each position before the translation
     * was added (three floats a vertex) for {@link #retranslate}.
     */
    void fill(long pointer, float[] data, int first, int count, @Nullable float[] untranslated) {
        int end = (first + count) * STRIDE;
        int u = 0;
        for (int i = first * STRIDE; i < end; i += STRIDE, pointer += VERTEX_SIZE) {
            float x = data[i], y = data[i + 1], z = data[i + 2];
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            float tnx = this.n00 * nx + this.n10 * ny + this.n20 * nz;
            float tny = this.n01 * nx + this.n11 * ny + this.n21 * nz;
            float tnz = this.n02 * nx + this.n12 * ny + this.n22 * nz;
            if (this.renormalize) {
                float scale = (float) (1.0 / Math.sqrt(tnx * tnx + tny * tny + tnz * tnz));
                tnx *= scale;
                tny *= scale;
                tnz *= scale;
            }
            float sx = this.p00 * x + this.p10 * y + this.p20 * z;
            float sy = this.p01 * x + this.p11 * y + this.p21 * z;
            float sz = this.p02 * x + this.p12 * y + this.p22 * z;
            if (untranslated != null) {
                untranslated[u++] = sx;
                untranslated[u++] = sy;
                untranslated[u++] = sz;
            }
            MemoryUtil.memPutFloat(pointer, sx + this.p30);
            MemoryUtil.memPutFloat(pointer + 4L, sy + this.p31);
            MemoryUtil.memPutFloat(pointer + 8L, sz + this.p32);
            MemoryUtil.memPutInt(pointer + 12L, this.abgr);
            MemoryUtil.memPutFloat(pointer + 16L, data[i + 3]);
            MemoryUtil.memPutFloat(pointer + 20L, data[i + 4]);
            MemoryUtil.memPutLong(pointer + 24L, this.overlayAndLight);
            MemoryUtil.memPutInt(pointer + 32L, normalByte(tnx) | normalByte(tny) << 8 | normalByte(tnz) << 16);
        }
    }

    /** Rewrites the positions of vertices {@link #fill} wrote, for this transform's translation. */
    void retranslate(long pointer, float[] untranslated, int count) {
        float tx = this.p30, ty = this.p31, tz = this.p32;
        for (int u = 0, end = count * 3; u < end; u += 3, pointer += VERTEX_SIZE) {
            MemoryUtil.memPutFloat(pointer, untranslated[u] + tx);
            MemoryUtil.memPutFloat(pointer + 4L, untranslated[u + 1] + ty);
            MemoryUtil.memPutFloat(pointer + 8L, untranslated[u + 2] + tz);
        }
    }

    /** A normal component as {@code BufferBuilder} stores it, as an unsigned byte. */
    private static int normalByte(float value) {
        return (int) (Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF;
    }
}
