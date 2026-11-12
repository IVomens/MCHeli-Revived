package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A named group of faces as it appears in the model file. {@link #bake()} flattens the faces into
 * one float array ({@link LegacyGL#BAKED_STRIDE} floats per vertex, every face stored as a quad)
 * that is streamed into the current {@link LegacyGL} frame. Baking needs no GL context, so models
 * can be loaded on any thread.
 */
public class GroupObject {

    private static final float[] NO_VERTICES = new float[0];
    /** Marks a level that would not have dropped enough faces to be worth keeping. */
    private static final float[] NO_GAIN = new float[0];

    public final String name;
    /** Parse-time faces; cleared by {@link #releaseFaces()} once baked. */
    public final ArrayList<ModelFace> faces = new ArrayList<>();
    private float[] baked;
    private int bakedVertices;
    /** Reduced meshes per level of {@link MeshDetail}, built the first time a level is needed. */
    private float[][] lods;
    private float minX, minY, minZ, maxX, maxY, maxZ;

    public GroupObject(String name) {
        this.name = name;
    }

    /** Builds (or rebuilds) the baked vertex array and the bounds from {@link #faces}. */
    public synchronized void bake() {
        int count = 0;
        for (ModelFace face : this.faces) {
            count += face.bakedVertexCount();
        }
        float[] data = count == 0 ? NO_VERTICES : new float[count * LegacyGL.BAKED_STRIDE];
        int vertices = 0;
        for (ModelFace face : this.faces) {
            vertices = face.bakeInto(data, vertices);
        }
        this.baked = data;
        this.bakedVertices = vertices;
        this.lods = null;
        this.computeBounds();
    }

    private void computeBounds() {
        float lowX = Float.POSITIVE_INFINITY, lowY = Float.POSITIVE_INFINITY, lowZ = Float.POSITIVE_INFINITY;
        float highX = Float.NEGATIVE_INFINITY, highY = Float.NEGATIVE_INFINITY, highZ = Float.NEGATIVE_INFINITY;
        int end = this.bakedVertices * LegacyGL.BAKED_STRIDE;
        for (int i = 0; i < end; i += LegacyGL.BAKED_STRIDE) {
            float x = this.baked[i], y = this.baked[i + 1], z = this.baked[i + 2];
            lowX = Math.min(lowX, x);
            lowY = Math.min(lowY, y);
            lowZ = Math.min(lowZ, z);
            highX = Math.max(highX, x);
            highY = Math.max(highY, y);
            highZ = Math.max(highZ, z);
        }
        this.minX = lowX;
        this.minY = lowY;
        this.minZ = lowZ;
        this.maxX = highX;
        this.maxY = highY;
        this.maxZ = highZ;
    }

    private void ensureBaked() {
        if (this.baked == null) {
            this.bake();
        }
    }

    public float[] getBakedData() {
        this.ensureBaked();
        return this.baked;
    }

    public int getBakedVertexCount() {
        this.ensureBaked();
        return this.bakedVertices;
    }

    /** Drops the per-face data once baked; the group can then only be rendered, not re-baked. */
    public void releaseFaces() {
        this.ensureBaked();
        this.faces.clear();
        this.faces.trimToSize();
    }

    /** Grows {@code bounds} (minX, minY, minZ, maxX, maxY, maxZ) to include this group. */
    void includeInBounds(float[] bounds) {
        this.ensureBaked();
        if (this.bakedVertices == 0) {
            return;
        }
        bounds[0] = Math.min(bounds[0], this.minX);
        bounds[1] = Math.min(bounds[1], this.minY);
        bounds[2] = Math.min(bounds[2], this.minZ);
        bounds[3] = Math.max(bounds[3], this.maxX);
        bounds[4] = Math.max(bounds[4], this.maxY);
        bounds[5] = Math.max(bounds[5], this.maxZ);
    }

    /**
     * Draws the group, switching to a reduced mesh when the current vehicle is far enough away
     * that a level's cell covers less than a pixel (see {@link MeshDetail}).
     */
    public void render() {
        float[] data = this.getBakedData();
        int vertices = this.bakedVertices;
        float[] reduced = this.reducedMesh(MeshDetail.levelFor(LegacyGL.modelUnitsPerPixel()));
        if (reduced != null) {
            data = reduced;
            vertices = reduced.length / LegacyGL.BAKED_STRIDE;
        }
        LegacyGL.emitBaked(data, 0, vertices);
    }

    /**
     * The vertices drawn at a level of detail, {@link LegacyGL#BAKED_STRIDE} floats each: the reduced
     * mesh of that level, or the full mesh where the level would not gain anything.
     */
    float[] vertices(int detailLevel) {
        float[] reduced = this.reducedMesh(detailLevel);
        if (reduced != null) {
            return reduced;
        }
        float[] data = this.getBakedData();
        int length = this.bakedVertices * LegacyGL.BAKED_STRIDE;
        return data.length == length ? data : Arrays.copyOf(data, length);
    }

    /** The reduced mesh of a level, or null to draw the full one (up close, small groups, no gain). */
    private float[] reducedMesh(int level) {
        if (level == MeshDetail.FULL || this.getBakedVertexCount() < MeshDetail.MIN_VERTICES) {
            return null;
        }
        return this.clusteredMesh(level);
    }

    /** Builds levels lazily; the immediate renderer and instancing workers may ask at the same time. */
    private synchronized float[] clusteredMesh(int level) {
        if (this.lods == null) {
            this.lods = new float[MeshDetail.levelCount()][];
        }
        float[] mesh = this.lods[level];
        if (mesh == null) {
            mesh = MeshClustering.cluster(this.baked, this.bakedVertices, MeshDetail.cell(level));
            if (mesh == null) {
                mesh = NO_GAIN;
            }
            this.lods[level] = mesh;
        }
        return mesh == NO_GAIN ? null : mesh;
    }

    public void renderLines() {
        float[] data = this.getBakedData();
        LegacyGL.emitBakedLines(data, 0, this.bakedVertices);
    }

    @Override
    public String toString() {
        return "GroupObject[" + this.name + ", " + this.getBakedVertexCount() + " vertices]";
    }
}
