package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;

import java.util.Arrays;

/**
 * Builds the reduced meshes used for distant vehicles by vertex clustering: every vertex is moved
 * to the centre of the grid cell it falls in, and quads that collapse to a line or a point are
 * dropped. Vertices shared by neighbouring faces land on the same spot, so the surface stays closed;
 * faces keep their order, so glass is still drawn after the hull behind it.
 * <p>
 * A mesh is only drawn at a level whose cell is smaller than a pixel on screen, so the snapping
 * itself cannot be seen. What it buys is that the hundreds of small faces making up rivets,
 * cockpit interiors and weapon details stop being sent once they are too small to show.
 */
final class MeshClustering {

    private static final int STRIDE = LegacyGL.BAKED_STRIDE;

    private MeshClustering() {
    }

    /**
     * Returns the clustered copy of {@code data} ({@code vertices} baked vertices, every face a
     * quad) for the given cell size, or null when it would not drop enough faces to be worth
     * keeping.
     */
    static float[] cluster(float[] data, int vertices, float cell) {
        float[] out = new float[vertices * STRIDE];
        float[] snapped = new float[12];
        int written = 0;
        int end = vertices * STRIDE;
        for (int quad = 0; quad + 4 * STRIDE <= end; quad += 4 * STRIDE) {
            for (int v = 0; v < 4; v++) {
                int i = quad + v * STRIDE;
                snapped[v * 3] = snap(data[i], cell);
                snapped[v * 3 + 1] = snap(data[i + 1], cell);
                snapped[v * 3 + 2] = snap(data[i + 2], cell);
            }
            if (distinctCorners(snapped) < 3) {
                continue;
            }
            for (int v = 0; v < 4; v++) {
                int from = quad + v * STRIDE;
                int to = written * STRIDE;
                System.arraycopy(data, from, out, to, STRIDE);
                out[to] = snapped[v * 3];
                out[to + 1] = snapped[v * 3 + 1];
                out[to + 2] = snapped[v * 3 + 2];
                written++;
            }
        }
        // a level that keeps nearly everything only costs memory
        if (written > vertices * 0.95) {
            return null;
        }
        return Arrays.copyOf(out, written * STRIDE);
    }

    private static float snap(float value, float cell) {
        return ((float) Math.floor(value / cell) + 0.5F) * cell;
    }

    private static int distinctCorners(float[] p) {
        int distinct = 1;
        for (int a = 1; a < 4; a++) {
            boolean seen = false;
            for (int b = 0; b < a && !seen; b++) {
                seen = p[a * 3] == p[b * 3] && p[a * 3 + 1] == p[b * 3 + 1] && p[a * 3 + 2] == p[b * 3 + 2];
            }
            if (!seen) {
                distinct++;
            }
        }
        return distinct;
    }
}
