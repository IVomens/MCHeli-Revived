package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;

/**
 * Geometry a vehicle renderer draws as one piece: a named part of a baked model, a whole model, or
 * the static crawler tracks. The immediate renderer emits it into the current {@link LegacyGL}
 * frame; an instancing renderer uploads its vertices once and draws copies of them.
 */
public interface PartMesh {

    /** Emits the mesh into the current {@link LegacyGL} frame, at the frame's level of detail. */
    void render();

    /**
     * The vertices at a level of detail, {@link LegacyGL#BAKED_STRIDE} floats each and every face a
     * quad: {@link MeshDetail#FULL} for the full mesh, otherwise a level of {@link MeshDetail}. The
     * array is shared; do not modify it.
     */
    float[] vertices(int detailLevel);
}
