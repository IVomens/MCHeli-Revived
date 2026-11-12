package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.client.overlay.HudCanvas;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A canvas that draws nothing and writes down every call instead: primitive, colour, coordinates and text,
 * one line per call. Coordinates use {@link Double#toString}, so two traces are equal only when the HUD
 * computed exactly the same numbers.
 * <p>
 * The lines keep the spelling of the first recordings, which were made through the GL-style calls of the old
 * scripted HUD: a scripted texture is two lines, the state it sets (default blending, white) and the quad.
 */
final class RecordingCanvas implements HudCanvas {

    /** Offset of the width and height fields in a PNG file (signature + IHDR chunk header). */
    private static final int PNG_SIZE_OFFSET = 16;

    private final int width;
    private final int height;
    private final int guiScale;
    private final List<String> calls = new ArrayList<>();

    RecordingCanvas(int width, int height, int guiScale) {
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
    }

    List<String> calls() {
        return this.calls;
    }

    void note(String line) {
        this.calls.add(line);
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public int guiScale() {
        return this.guiScale;
    }

    @Override
    public void pushPose() {
        this.calls.add("push");
    }

    @Override
    public void popPose() {
        this.calls.add("pop");
    }

    @Override
    public void translate(double x, double y) {
        this.calls.add("translate " + x + ", " + y + ", " + 0.0);
    }

    @Override
    public void scale(float x, float y) {
        this.calls.add("scale " + x + ", " + y);
    }

    @Override
    public void rotate(float degrees) {
        this.calls.add("rotate " + degrees + " about 0.0, 0.0, 1.0");
    }

    @Override
    public void resetTransform() {
        this.calls.add("reset transform");
    }

    @Override
    public void lines(double[] coords, int length, LineMode mode, int argb) {
        this.calls.add("lines " + mode + " " + color(argb) + " " + points(coords, length));
    }

    @Override
    public void dashedLines(double[] coords, int length, LineMode mode, int argb, int factor, int pattern) {
        this.calls.add("stipple " + mode + " " + color(argb) + " factor " + factor + " pattern 0x"
                + Integer.toHexString(pattern).toUpperCase(Locale.ROOT) + " " + points(coords, length));
    }

    @Override
    public void points(double[] coords, int length, int argb, float sizePx) {
        this.calls.add("points " + color(argb) + " size " + sizePx + " " + points(coords, length));
    }

    @Override
    public void fillRect(double x1, double y1, double x2, double y2, int argb) {
        this.calls.add("rect " + color(argb) + " (" + x1 + ", " + y1 + ") (" + x2 + ", " + y2 + ")");
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        this.calls.add("text " + color(argb) + " (" + x + ", " + y + ") \"" + text + "\""
                + (shadow ? "" : " without shadow"));
    }

    @Override
    public void centeredText(String text, int x, int y, int argb) {
        this.calls.add("centered text " + color(argb) + " (" + x + ", " + y + ") \"" + text + "\"");
    }

    /** Six pixels a character, so layouts that measure text are recorded too. */
    @Override
    public int textWidth(String text) {
        return text.length() * 6;
    }

    @Override
    public void styledText(String text, float x, float y, int argb, Typeface typeface) {
        this.calls.add(typeface + " text " + color(argb) + " (" + x + ", " + y + ") \"" + text + "\"");
    }

    @Override
    public float styledTextWidth(String text, Typeface typeface) {
        return text.length() * 6;
    }

    @Override
    public void texture(ResourceLocation texture, double x, double y, double width, double height, Tint tint) {
        this.calls.add("texture " + texture + " (" + x + ", " + y + ") size (" + width + ", " + height + ") tint "
                + tint);
    }

    @Override
    public void textureRegion(ResourceLocation texture, double left, double top, double width, double height,
                              double u, double v, double uWidth, double vHeight, float rotation, int textureWidth,
                              int textureHeight) {
        this.calls.add("prepare texture");
        this.calls.add("texture " + texture + " (" + left + ", " + top + ") size (" + width + ", " + height
                + ") uv (" + u + ", " + v + ") uv size (" + uWidth + ", " + vHeight + ") rotation " + rotation
                + " of " + textureWidth + "x" + textureHeight);
    }

    /** Reads the size from the PNG on the classpath, like the game reads it from its resources. */
    @Override
    public int[] textureSize(ResourceLocation texture) {
        String resource = "assets/" + texture.getNamespace() + "/" + texture.getPath();
        try (InputStream in = RecordingCanvas.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return new int[]{0, 0};
            DataInputStream data = new DataInputStream(in);
            data.skipNBytes(PNG_SIZE_OFFSET);
            return new int[]{data.readInt(), data.readInt()};
        } catch (IOException e) {
            return new int[]{0, 0};
        }
    }

    @Override
    public void strokeLine(float x1, float y1, float x2, float y2, float width, int argb) {
        this.calls.add("stroke " + color(argb) + " width " + width + " (" + x1 + ", " + y1 + ") (" + x2 + ", " + y2
                + ")");
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb) {
        this.calls.add("fill " + color(argb) + " (" + x + ", " + y + ") size (" + width + ", " + height + ")");
    }

    @Override
    public void flush() {
        this.calls.add("flush");
    }

    private static String color(int argb) {
        return String.format("#%08X", argb);
    }

    private static String points(double[] coords, int length) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i + 1 < length; i += 2) {
            text.append(i == 0 ? "" : " ").append(coords[i]).append(',').append(coords[i + 1]);
        }
        if ((length & 1) == 1) {
            text.append(" (odd value ").append(coords[length - 1]).append(')');
        }
        return text.append(']').toString();
    }
}
