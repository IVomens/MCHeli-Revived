package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;

/**
 * A named part of a baked model, resolved once so renderers can draw it every frame without a name
 * lookup. A part can span several file groups: a Metasequoia {@code $part} object plus the
 * unnamed objects that follow it, or groups that share a name.
 */
public final class BakedPart implements PartMesh {

    private final String name;
    private final GroupObject[] groups;
    private final float extent;

    BakedPart(String name, GroupObject[] groups) {
        this.name = name;
        this.groups = groups;
        float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (GroupObject group : groups) {
            group.includeInBounds(bounds);
        }
        this.extent = halfDiagonal(bounds[3] - bounds[0], bounds[4] - bounds[1], bounds[5] - bounds[2]);
    }

    static float halfDiagonal(float sizeX, float sizeY, float sizeZ) {
        if (!(sizeX >= 0.0F && sizeY >= 0.0F && sizeZ >= 0.0F)) {
            return 0.0F;
        }
        return 0.5F * (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);
    }

    public String name() {
        return this.name;
    }

    /** Half the diagonal of the part's own bounding box: how big it is, wherever it sits. */
    public float extent() {
        return this.extent;
    }

    @Override
    public void render() {
        for (GroupObject group : this.groups) {
            group.render();
        }
    }

    GroupObject[] groupsForIndex() {
        return this.groups;
    }

    /**
     * All vertices of the part at a level of detail in one array (a copy when it spans several
     * groups). Each group switches level on its own, exactly as {@link #render()} does.
     */
    @Override
    public float[] vertices(int detailLevel) {
        if (this.groups.length == 1) {
            return this.groups[0].vertices(detailLevel);
        }
        float[][] parts = new float[this.groups.length][];
        int total = 0;
        for (int i = 0; i < this.groups.length; i++) {
            parts[i] = this.groups[i].vertices(detailLevel);
            total += parts[i].length;
        }
        float[] data = new float[total];
        int offset = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, data, offset, part.length);
            offset += part.length;
        }
        return data;
    }

    /** All vertices of the full part, {@link LegacyGL#BAKED_STRIDE} floats each. Do not modify it. */
    public float[] bakedData() {
        return this.vertices(MeshDetail.FULL);
    }
}
