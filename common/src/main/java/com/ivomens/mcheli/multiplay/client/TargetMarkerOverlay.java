package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.multiplay.TargetMarkerObject;
import com.ivomens.mcheli.core.target.TargetType;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Screen markers for spotted entities, team mates and marked points, as {@link TeamMarkers} projected them
 * for this frame: triangles above entities, rhombi around points.
 */
public class TargetMarkerOverlay extends HudOverlay {

    /** Marker colour per {@link TargetType} ordinal. */
    private static final int[] COLORS = {0, 0xCFCFCFCF, 0xCFFF0000, 0xCFFFFF00, 0xCF00FFFF, 0xCFFF4F1F, 0xFFFF0000, 0};
    private static final double EDGE_MARGIN = 30.0;
    /** Point coordinates are only shown while the marker is this close to the crosshair. */
    private static final double COORDINATES_RADIUS = 20.0;
    private static final int ALL_TIPS = 15;
    private static final int TIP_LEFT = 1;
    private static final int TIP_BOTTOM = 2;
    private static final int TIP_RIGHT = 4;
    private static final int TIP_TOP = 8;

    private final TeamMarkers markers;
    private final double[] triangle = new double[6];
    private final DoubleArrayList lines = new DoubleArrayList(32);

    public TargetMarkerOverlay(Minecraft minecraft, TeamMarkers markers) {
        super(minecraft);
        this.markers = markers;
    }

    public static boolean isEnableEntityMarker() {
        return McheliConfig.render().displayEntityMarker()
                && (Minecraft.getInstance().hasSingleplayerServer() || McheliConfig.server().enableEntityMarker())
                && McheliConfig.render().entityMarkerSize() > 0.0;
    }

    /** Appends the line segments of a rhombus marker; the {@code tips} bits select its four tips. */
    public static void appendRhombus(DoubleArrayList out, int tips, double x, double y, double size) {
        double length = size * 2.0;
        double wing = length / 3.0;
        if ((tips & TIP_LEFT) != 0) {
            addSegments(out, x - length, y, x - length + wing, y - wing, x - length, y, x - length + wing, y + wing);
        }
        if ((tips & TIP_RIGHT) != 0) {
            addSegments(out, x + length, y, x + length - wing, y - wing, x + length, y, x + length - wing, y + wing);
        }
        if ((tips & TIP_TOP) != 0) {
            addSegments(out, x, y - length, x + wing, y - length + wing, x, y - length, x - wing, y - length + wing);
        }
        if ((tips & TIP_BOTTOM) != 0) {
            addSegments(out, x, y + length, x + wing, y + length - wing, x, y + length, x - wing, y + length - wing);
        }
    }

    private static void addSegments(DoubleArrayList out, double... coordinates) {
        out.addElements(out.size(), coordinates);
    }

    /** Draws a rhombus marker on {@code canvas}. */
    public static void drawRhombus(HudCanvas canvas, int tips, double x, double y, double size, int color) {
        DoubleArrayList list = new DoubleArrayList(32);
        appendRhombus(list, tips, x, y, size);
        canvas.lines(list.elements(), list.size(), HudCanvas.LineMode.LINES, color);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return player != null && player.level() != null;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        this.lineWidth(scaleFactor * 2);
        if (this.isDrawGui(player) && isEnableEntityMarker()) {
            this.drawMarkers();
        }
    }

    private void drawMarkers() {
        for (TeamMarkers.Marker marker : this.markers.frameMarkers()) {
            int color = COLORS[marker.type()];
            // Behind the camera, push the marker off the side of the screen it is on.
            double x = marker.behind() ? (marker.x() < this.width / 2.0 ? 10000.0 : -10000.0) : marker.x();
            double y = marker.y();
            if (marker.type() < TargetType.POINT.ordinal()) {
                drawEntityMarker(marker, x, y, color);
            } else if (marker.type() == TargetType.POINT.ordinal() && marker.target() != null) {
                drawPointMarker(marker, x, y, color);
            }
        }
    }

    private void drawEntityMarker(TeamMarkers.Marker marker, double x, double y, int color) {
        if (marker.behind() || x < 0.0 || x > this.width || y < 0.0 || y > this.height) return;
        double size = McheliConfig.render().entityMarkerSize();
        double[] t = this.triangle;
        t[0] = x + size / 2.0;
        t[1] = y - 10.0 - size;
        t[2] = x - size / 2.0;
        t[3] = y - 10.0 - size;
        t[4] = x;
        t[5] = y - 10.0;
        this.fillTriangles(t, color);
    }

    /** On screen a rhombus with the distance; off screen an arrow tip at the edge it lies beyond. */
    private void drawPointMarker(TeamMarkers.Marker marker, double x, double y, int color) {
        TargetMarkerObject target = marker.target();
        double size = McheliConfig.render().blockMarkerSize();
        double halfWidth = this.width / 2.0;
        double halfHeight = this.height / 2.0;
        DoubleArrayList out = this.lines;
        out.clear();

        if (!marker.behind() && x >= 0.0 && x <= this.width - 20 && y >= 0.0 && y <= this.height - 40) {
            double distance = Math.sqrt(this.mc.player.distanceToSqr(target.getX(), target.getY(), target.getZ()));
            this.drawCenteredString(String.format("%.0fm", distance), (int) x, (int) (y + size * 1.1 + 16.0), color);
            if (Math.abs(x - halfWidth) <= COORDINATES_RADIUS && Math.abs(y - halfHeight) <= COORDINATES_RADIUS) {
                int textX = (int) (x + size + 18.0);
                this.drawString(String.format("x : %.0f", target.getX()), textX, (int) y - 12, color);
                this.drawString(String.format("y : %.0f", target.getY()), textX, (int) y - 4, color);
                this.drawString(String.format("z : %.0f", target.getZ()), textX, (int) y + 4, color);
            }
            appendRhombus(out, ALL_TIPS, x, y, size);
        } else {
            if (x < EDGE_MARGIN) {
                appendRhombus(out, TIP_LEFT, EDGE_MARGIN, halfHeight, size);
            } else if (x > this.width - EDGE_MARGIN) {
                appendRhombus(out, TIP_RIGHT, this.width - EDGE_MARGIN, halfHeight, size);
            }
            if (y < EDGE_MARGIN) {
                appendRhombus(out, TIP_TOP, halfWidth, EDGE_MARGIN, size);
            } else if (y > this.height - EDGE_MARGIN * 2.0) {
                appendRhombus(out, TIP_BOTTOM, halfWidth, this.height - EDGE_MARGIN * 2.0, size);
            }
        }
        if (!out.isEmpty()) {
            this.drawLine(out.elements(), out.size(), color, HudCanvas.LineMode.LINES);
        }
    }
}
