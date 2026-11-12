package com.ivomens.mcheli.hud.client.modern;

import com.ivomens.mcheli.client.overlay.HudCanvas;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The modern HUD's drawing vocabulary on a {@link HudCanvas}: thin strokes, boxed readouts, bars and tags in
 * the HUD fonts. Coordinates are in a virtual 640 x 360 canvas scaled to the window height, so layouts are
 * written once and look the same at every GUI scale.
 */
final class HologramPen {

    static final float WIDTH = 640;
    static final float HEIGHT = 360;
    /** Faint dark fill behind boxed readouts. */
    static final int BACKING = 0x66041410;
    private static final float STROKE_WIDTH = 0.75F;

    private final HudCanvas canvas;

    HologramPen(HudCanvas canvas) {
        this.canvas = canvas;
    }

    HudCanvas canvas() {
        return this.canvas;
    }

    /** Scales the canvas so that {@link #WIDTH} x {@link #HEIGHT} fills the screen height. */
    void begin(float userScale) {
        float screenHeight = this.canvas.height();
        float screenWidth = this.canvas.width();
        float scale = screenHeight / HEIGHT * userScale;
        this.canvas.pushPose();
        this.canvas.translate(screenWidth / 2F, screenHeight / 2F);
        this.canvas.scale(scale, scale);
        this.canvas.translate(-WIDTH / 2F, -HEIGHT / 2F);
    }

    void end() {
        this.canvas.flush();
        this.canvas.popPose();
    }

    // Shapes

    void line(float x1, float y1, float x2, float y2, int color) {
        this.canvas.strokeLine(x1, y1, x2, y2, STROKE_WIDTH, color);
    }

    void dashedLine(float x1, float y1, float x2, float y2, float dash, float gap, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) return;
        float ux = dx / length;
        float uy = dy / length;
        for (float d = 0; d < length; d += dash + gap) {
            float e = Math.min(length, d + dash);
            line(x1 + ux * d, y1 + uy * d, x1 + ux * e, y1 + uy * e, color);
        }
    }

    void rect(float x, float y, float w, float h, int color) {
        line(x, y, x + w, y, color);
        line(x + w, y, x + w, y + h, color);
        line(x + w, y + h, x, y + h, color);
        line(x, y + h, x, y, color);
    }

    void fill(float x, float y, float w, float h, int color) {
        this.canvas.fill(x, y, w, h, color);
    }

    void circle(float cx, float cy, float r, int color) {
        int segments = Math.max(16, (int) (r * 1.5F));
        float px = cx + r;
        float py = cy;
        for (int i = 1; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            float nx = cx + Mth.cos(a) * r;
            float ny = cy + Mth.sin(a) * r;
            line(px, py, nx, ny, color);
            px = nx;
            py = ny;
        }
    }

    /** Outlined box with a faint backing, used behind every changing number. */
    void readout(float x, float y, float w, float h, String text, int color) {
        fill(x, y, w, h, BACKING);
        rect(x, y, w, h, color);
        monoCentered(text, x + w / 2, y + (h - 10) / 2, color);
    }

    void bar(float x, float y, float w, float h, float fraction, int color) {
        rect(x, y, w, h, color);
        float f = Mth.clamp(fraction, 0, 1);
        if (f > 0) fill(x + 1, y + 1, (w - 2) * f, h - 2, withAlpha(color, 0.85F));
    }

    // Text

    void mono(String text, float x, float y, int color) {
        this.canvas.styledText(text, x, y, color, HudCanvas.Typeface.MONO);
    }

    void monoRight(String text, float right, float y, int color) {
        mono(text, right - this.canvas.styledTextWidth(text, HudCanvas.Typeface.MONO), y, color);
    }

    void monoCentered(String text, float cx, float y, int color) {
        mono(text, cx - this.canvas.styledTextWidth(text, HudCanvas.Typeface.MONO) / 2F, y, color);
    }

    void label(String text, float x, float y, int color) {
        this.canvas.styledText(text, x, y, color, HudCanvas.Typeface.LABEL);
    }

    void labelCentered(String text, float cx, float y, int color) {
        label(text, cx - labelWidth(text) / 2F, y, color);
    }

    float labelWidth(String text) {
        return this.canvas.styledTextWidth(text, HudCanvas.Typeface.LABEL);
    }

    /** Alert tag: outlined label in the alert colour. */
    void tag(String text, float cx, float y, int color) {
        float w = labelWidth(text) + 10;
        fill(cx - w / 2, y, w, 15, BACKING);
        rect(cx - w / 2, y, w, 15, color);
        labelCentered(text, cx, y + 3, color);
    }

    // Colour

    static int withAlpha(int argb, float alpha) {
        return FastColor.ARGB32.color((int) (alpha * 255), argb);
    }
}
