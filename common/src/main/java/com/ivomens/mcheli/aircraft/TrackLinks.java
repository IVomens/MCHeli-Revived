package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.Parts.TrackPoint;
import com.ivomens.mcheli.content.def.vehicle.Parts;
import com.ivomens.mcheli.core.content.schema.ContentValueException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays the links of a crawler track along its outline. YAML files space the links evenly along the outline;
 * legacy TXT files used an older spacing that the tracks drawn from them depend on, so they keep it.
 */
final class TrackLinks {

    /** Links sit a little closer than the written segment length. */
    private static final float SPACING = 0.9F;
    private static final int MIN_POINTS = 4;
    /** Neighbouring link angles closer than this are averaged. */
    private static final double SMOOTHING = 0.3;
    private static final float MAX_SMOOTHED_TURN = 100.0F;

    private TrackLinks() {
    }

    /**
     * The track, or null for a legacy TXT track with fewer than four points, which draws nothing but keeps
     * its place in the list.
     */
    static VehicleParts.CrawlerTrack of(Parts.CrawlerTrack track, boolean legacySpacing) {
        List<TrackPoint> points = track.points();
        if (points.size() < MIN_POINTS) {
            if (legacySpacing) {
                return null;
            }
            throw new ContentValueException("a crawler track needs at least 4 points in its TrackList");
        }
        int count = points.size();
        double[] cx = new double[count];
        double[] cy = new double[count];
        for (int i = 0; i < count; i++) {
            TrackPoint point = points.get(track.reverse() ? count - i - 1 : i);
            cx[i] = point.x();
            cy[i] = point.y();
        }
        float length = track.segmentLength() * SPACING;
        List<float[]> links = legacySpacing ? legacyLinks(cx, cy, length) : evenLinks(cx, cy, length);
        List<VehicleParts.CrawlerTrackPrm> placed = new ArrayList<>(links.size());
        for (int i = 0; i < links.size(); i++) {
            float[] link = links.get(i);
            placed.add(new VehicleParts.CrawlerTrackPrm(link[0], link[1], angle(links, i, legacySpacing)));
        }
        return new VehicleParts.CrawlerTrack(new VehicleParts.DrawnPart(track.position(), track.rotation(),
                track.name()), length, cx, cy, placed, track.offset());
    }

    /** The older spacing: a running distance that restarts its count on each side of the outline. */
    private static List<float[]> legacyLinks(double[] cx, double[] cy, float length) {
        List<float[]> links = new ArrayList<>();
        links.add(new float[]{(float) cx[0], (float) cy[0]});
        double distance = 0.0;
        for (int i = 0; i < cx.length; i++) {
            double x = cx[(i + 1) % cx.length] - cx[i];
            double y = cy[(i + 1) % cx.length] - cy[i];
            distance += Math.sqrt(x * x + y * y);
            double travelled = distance;
            for (int j = 1; distance >= length; j++) {
                links.add(new float[]{(float) (cx[i] + x * (length * j / travelled)),
                        (float) (cy[i] + y * (length * j / travelled))});
                distance -= length;
            }
        }
        return links;
    }

    /** Links exactly {@code length} apart along the outline. */
    private static List<float[]> evenLinks(double[] cx, double[] cy, float length) {
        List<float[]> links = new ArrayList<>();
        links.add(new float[]{(float) cx[0], (float) cy[0]});
        double carry = 0.0;
        for (int i = 0; i < cx.length; i++) {
            int next = (i + 1) % cx.length;
            double dx = cx[next] - cx[i];
            double dy = cy[next] - cy[i];
            double side = Math.hypot(dx, dy);
            double ux = dx / side;
            double uy = dy / side;
            double px = cx[i];
            double py = cy[i];
            double need = length - carry;
            double left = side;
            while (left >= need) {
                px += ux * need;
                py += uy * need;
                links.add(new float[]{(float) px, (float) py});
                left -= need;
                need = length;
            }
            carry = left;
        }
        return links;
    }

    /**
     * The angle a link lies at, between the directions to its neighbours. Both spacings keep their own
     * rounding: the legacy one converts to degrees and compares in doubles, the even one in floats.
     */
    private static float angle(List<float[]> links, int i, boolean legacy) {
        int count = links.size();
        float[] previous = links.get((i + count - 1) % count);
        float[] current = links.get(i);
        float[] next = links.get((i + 1) % count);
        float toPrevious = degrees(Math.atan2(previous[0] - current[0], previous[1] - current[1]), legacy);
        float toNext = degrees(Math.atan2(next[0] - current[0], next[1] - current[1]), legacy);
        float back = (toPrevious + 360.0F) % 360.0F;
        float forward = toNext + 180.0F;
        boolean apart = legacy ? forward < back - SMOOTHING || forward > back + SMOOTHING
                : forward < back - (float) SMOOTHING || forward > back + (float) SMOOTHING;
        if (apart && forward - back < MAX_SMOOTHED_TURN && forward - back > -MAX_SMOOTHED_TURN) {
            forward = (forward + back) / 2.0F;
        }
        return forward;
    }

    private static float degrees(double radians, boolean legacy) {
        return (float) (legacy ? radians * 180.0 / Math.PI : Math.toDegrees(radians));
    }
}
