package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.client.render.LegacyGL;

import javax.annotation.Nullable;

/**
 * Crawler tracks drawn as one static mesh: the model's tread template laid along the first track path
 * of the definition and mirrored to the other side. Much cheaper than the animated tracks, so it is used
 * for parked and distant vehicles.
 */
public final class StaticTracks {

    private StaticTracks() {
    }

    /** The whole model at rest: every part except the tread template, plus the static tracks. */
    public static void renderStatic(BakedModelBase body, @Nullable VehicleSpec info) {
        body.renderStaticParts();
        if (info != null) {
            PartMesh tracks = of(body, info);
            if (tracks != null) {
                tracks.render();
            }
        }
    }

    /**
     * The static track mesh of the model, built from the first definition that asks; null if the model
     * has no tread template or the definition no tracks.
     */
    @Nullable
    public static PartMesh of(BakedModelBase body, VehicleSpec info) {
        if (!body.hasTrackTemplate() || info.parts().crawlerTracks().isEmpty()) {
            return null;
        }
        PartMesh tracks = body.staticTracks();
        if (tracks == null) {
            tracks = body.setStaticTracks(bakeCrawlerTracks(info.parts().crawlerTracks().get(0),
                    body.trackTreadVertices()));
        }
        return tracks;
    }

    private static float[] bakeCrawlerTracks(VehicleParts.CrawlerTrack track, float[] tread) {
        final int stride = LegacyGL.BAKED_STRIDE;
        int pointCount = track.lp.size() - 1;
        if (pointCount <= 0) {
            return new float[0];
        }
        int oneSide = pointCount * tread.length;
        float[] baked = new float[oneSide * 2];
        int cursor = 0;
        for (int i = 0; i < pointCount; i++) {
            VehicleParts.CrawlerTrackPrm point = track.lp.get(i);
            float radians = (float) Math.toRadians(point.r);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            for (int j = 0; j < tread.length; j += stride) {
                float y = tread[j + 1], z = tread[j + 2];
                float ny = tread[j + 6], nz = tread[j + 7];
                baked[cursor++] = tread[j];
                baked[cursor++] = (y * cos + z * sin) + point.x;
                baked[cursor++] = (-y * sin + z * cos) + point.y;
                baked[cursor++] = tread[j + 3];
                baked[cursor++] = tread[j + 4];
                baked[cursor++] = tread[j + 5];
                baked[cursor++] = ny * cos + nz * sin;
                baked[cursor++] = -ny * sin + nz * cos;
            }
        }

        // mirror to the other side; reversing each quad's vertex order keeps the winding
        final int quad = 4 * stride;
        for (int i = 0; i + quad <= oneSide; i += quad) {
            for (int v = 0; v < 4; v++) {
                int src = i + v * stride;
                int dst = oneSide + i + (3 - v) * stride;
                System.arraycopy(baked, src, baked, dst, stride);
                baked[dst] = -baked[src];
                baked[dst + 5] = -baked[src + 5];
            }
        }
        return baked;
    }
}
