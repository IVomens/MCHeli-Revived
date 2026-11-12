package com.ivomens.mcheli.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Immediate-style coloured line helpers on top of the current {@link LegacyGL} frame. */
public class RenderLib {

    public static final int MODE_POINTS = 0;
    public static final int MODE_LINES = 1;
    public static final int MODE_LINE_LOOP = 2;
    public static final int MODE_LINE_STRIP = 3;

    public static void drawLine(Vec3[] points, int color) {
        drawLine(points, color, MODE_LINES, 1);
    }

    /**
     * @param color ARGB
     * @param mode  primitive: {@link #MODE_POINTS}, {@link #MODE_LINES}, {@link #MODE_LINE_LOOP} or {@link #MODE_LINE_STRIP}
     * @param width line width / point size in pixels
     */
    public static void drawLine(Vec3[] points, int color, int mode, int width) {
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = (color >>> 24 & 0xFF) / 255.0F;
        int n = points.length;
        if (n == 0) {
            return;
        }

        if (mode == MODE_POINTS) {
            // GL points do not exist in the core pipeline; draw tiny camera-facing squares instead
            float s = 0.01F * Math.max(1, width);
            VertexConsumer vc = LegacyGL.colorBuffer(false, false);
            for (Vec3 v : points) {
                drawPoint(vc, v.x, v.y, v.z, s, r, g, b, a);
            }
            return;
        }

        VertexConsumer vc = LegacyGL.lineBuffer(width, false);
        switch (mode) {
            case MODE_LINE_LOOP, MODE_LINE_STRIP -> {
                for (int i = 0; i + 1 < n; i++) {
                    LegacyGL.line(vc, points[i].x, points[i].y, points[i].z, points[i + 1].x, points[i + 1].y, points[i + 1].z, r, g, b, a);
                }
                if (mode == MODE_LINE_LOOP && n > 2) {
                    LegacyGL.line(vc, points[n - 1].x, points[n - 1].y, points[n - 1].z, points[0].x, points[0].y, points[0].z, r, g, b, a);
                }
            }
            default -> {
                for (int i = 0; i + 1 < n; i += 2) {
                    LegacyGL.line(vc, points[i].x, points[i].y, points[i].z, points[i + 1].x, points[i + 1].y, points[i + 1].z, r, g, b, a);
                }
            }
        }
    }

    /** Small axis-aligned box standing in for a GL point. */
    public static void drawPoint(VertexConsumer vc, double x, double y, double z, float s, float r, float g, float b, float a) {
        float x0 = (float) (x - s), x1 = (float) (x + s);
        float y0 = (float) (y - s), y1 = (float) (y + s);
        float z0 = (float) (z - s), z1 = (float) (z + s);
        // six faces
        quad(vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        LegacyGL.vertexColor(vc, ax, ay, az, r, g, b, a);
        LegacyGL.vertexColor(vc, bx, by, bz, r, g, b, a);
        LegacyGL.vertexColor(vc, cx, cy, cz, r, g, b, a);
        LegacyGL.vertexColor(vc, dx, dy, dz, r, g, b, a);
    }
}
