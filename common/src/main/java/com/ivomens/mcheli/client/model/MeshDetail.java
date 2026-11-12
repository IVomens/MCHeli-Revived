package com.ivomens.mcheli.client.model;

/**
 * The levels of detail of baked meshes. Level {@code n} is the mesh clustered on a grid of
 * {@link #cell(int)} model units (see {@link MeshClustering}); a level is used once one screen pixel
 * spans at least that many units, so its snapping cannot be seen.
 */
public final class MeshDetail {

    /** The full mesh. */
    public static final int FULL = -1;
    /** Cell sizes, in model units, of the reduced meshes. */
    private static final float[] CELLS = {1.0F / 16, 1.0F / 8, 1.0F / 4, 1.0F / 2, 1.0F};
    /** Groups this small are drawn as they are at any distance. */
    static final int MIN_VERTICES = 32;

    private MeshDetail() {
    }

    /** The number of reduced levels. */
    public static int levelCount() {
        return CELLS.length;
    }

    /** The cell size of a reduced level, in model units. */
    static float cell(int level) {
        return CELLS[level];
    }

    /**
     * The coarsest level whose cells are still under a pixel when one pixel spans
     * {@code unitsPerPixel} model units, or {@link #FULL} up close (and for 0, meaning full detail).
     */
    public static int levelFor(float unitsPerPixel) {
        if (!(unitsPerPixel >= CELLS[0])) {
            return FULL;
        }
        int level = 0;
        while (level + 1 < CELLS.length && CELLS[level + 1] <= unitsPerPixel) {
            level++;
        }
        return level;
    }
}
