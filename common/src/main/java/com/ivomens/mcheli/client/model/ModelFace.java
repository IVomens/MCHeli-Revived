package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * One polygon of a parsed model. Faces are only used while loading: {@link GroupObject} bakes them
 * into flat vertex arrays (see {@link #bakeInto}) which are what actually gets rendered.
 */
public final class ModelFace {

    /** Vertex indices within the object; only set by the Metasequoia loader, for smooth normals. */
    @Nullable
    private final int[] verticesID;
    private final ModelVertex[] vertices;
    @Nullable
    private final TextureCoordinate[] textureCoordinates;
    @Nullable
    private ModelVertex[] vertexNormals;
    private final ModelVertex faceNormal;

    /**
     * A face with its corners; the arrays are kept, not copied, and the face normal is computed from the first
     * three corners.
     *
     * @param textureCoordinates one per corner, or null for none
     * @param vertexNormals      one per corner (an entry may be null), or null to use the face normal
     * @param verticesID         the corners' vertex indices within their object, or null when not needed
     */
    public ModelFace(ModelVertex[] vertices, @Nullable TextureCoordinate[] textureCoordinates,
                     @Nullable ModelVertex[] vertexNormals, @Nullable int[] verticesID) {
        this.vertices = vertices;
        this.textureCoordinates = textureCoordinates;
        this.vertexNormals = vertexNormals;
        this.verticesID = verticesID;
        this.faceNormal = this.calculateFaceNormal();
    }

    /** The corners' vertex indices within their object, or null when the loader does not need them. */
    @Nullable
    public int[] verticesID() {
        return this.verticesID;
    }

    /** The normal of the plane through the first three corners. */
    public ModelVertex faceNormal() {
        return this.faceNormal;
    }

    /** Sets the normal of each corner; a null entry uses the face normal. */
    public void setVertexNormals(ModelVertex[] vertexNormals) {
        this.vertexNormals = vertexNormals;
    }

    /** Number of quad-mode vertices this face bakes to (triangles repeat their last vertex). */
    public int bakedVertexCount() {
        int n = this.vertices == null ? 0 : this.vertices.length;
        if (n < 3) {
            return 0;
        }
        if (n <= 4) {
            return 4;
        }
        // polygon with more than four corners: triangle fan, one degenerate quad per triangle
        return (n - 2) * 4;
    }

    /**
     * Writes this face into {@code out} at vertex index {@code vertexIndex} using the
     * {@link LegacyGL#BAKED_STRIDE} layout and returns the next free vertex index.
     */
    public int bakeInto(float[] out, int vertexIndex) {
        int n = this.vertices == null ? 0 : this.vertices.length;
        if (n < 3) {
            return vertexIndex;
        }
        if (n == 3) {
            vertexIndex = this.put(out, vertexIndex, 0);
            vertexIndex = this.put(out, vertexIndex, 1);
            vertexIndex = this.put(out, vertexIndex, 2);
            return this.put(out, vertexIndex, 2);
        }
        if (n == 4) {
            for (int i = 0; i < 4; i++) {
                vertexIndex = this.put(out, vertexIndex, i);
            }
            return vertexIndex;
        }
        for (int i = 1; i < n - 1; i++) {
            vertexIndex = this.put(out, vertexIndex, 0);
            vertexIndex = this.put(out, vertexIndex, i);
            vertexIndex = this.put(out, vertexIndex, i + 1);
            vertexIndex = this.put(out, vertexIndex, i + 1);
        }
        return vertexIndex;
    }

    private int put(float[] out, int vertexIndex, int i) {
        int o = vertexIndex * LegacyGL.BAKED_STRIDE;
        ModelVertex v = this.vertices[i];
        out[o] = v.x();
        out[o + 1] = v.y();
        out[o + 2] = v.z();
        if (this.textureCoordinates != null && this.textureCoordinates.length > i) {
            out[o + 3] = this.textureCoordinates[i].u;
            out[o + 4] = this.textureCoordinates[i].v;
        } else {
            out[o + 3] = 0.0F;
            out[o + 4] = 0.0F;
        }
        ModelVertex nrm = this.vertexNormals != null && i < this.vertexNormals.length && this.vertexNormals[i] != null
                ? this.vertexNormals[i] : this.faceNormal;
        out[o + 5] = nrm.x();
        out[o + 6] = nrm.y();
        out[o + 7] = nrm.z();
        return vertexIndex + 1;
    }

    private ModelVertex calculateFaceNormal() {
        ModelVertex v0 = this.vertices[0];
        ModelVertex v1 = this.vertices[1];
        ModelVertex v2 = this.vertices[2];
        float ax = v1.x() - v0.x();
        float ay = v1.y() - v0.y();
        float az = v1.z() - v0.z();
        float bx = v2.x() - v0.x();
        float by = v2.y() - v0.y();
        float bz = v2.z() - v0.z();
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1.0E-4) {
            return new ModelVertex(0.0F, 0.0F, 0.0F);
        }
        return new ModelVertex((float) (cx / len), (float) (cy / len), (float) (cz / len));
    }

    @Override
    public String toString() {
        return "ModelFace[id:" + Arrays.toString(this.verticesID) + "]";
    }
}
