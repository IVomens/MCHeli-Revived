package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.client.overlay.HudCanvas;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A canvas that draws nothing and keeps the text drawn on it and the translations, so a game test can see what
 * the HUD widget layer drew and where, on a server without a screen.
 */
final class TextCanvas implements HudCanvas {

    private final List<String> texts = new ArrayList<>();
    private final List<double[]> translations = new ArrayList<>();
    private int shapes;

    /** Every text drawn, in order. */
    List<String> texts() {
        return this.texts;
    }

    /** Every translation, in order, as {x, y}. */
    List<double[]> translations() {
        return this.translations;
    }

    /** How many lines and fills were drawn. */
    int shapes() {
        return this.shapes;
    }

    @Override
    public int width() {
        return 640;
    }

    @Override
    public int height() {
        return 360;
    }

    @Override
    public int guiScale() {
        return 2;
    }

    @Override
    public void pushPose() {
    }

    @Override
    public void popPose() {
    }

    @Override
    public void translate(double x, double y) {
        this.translations.add(new double[]{x, y});
    }

    @Override
    public void scale(float x, float y) {
    }

    @Override
    public void rotate(float degrees) {
    }

    @Override
    public void resetTransform() {
    }

    @Override
    public void lines(double[] coords, int length, LineMode mode, int argb) {
        this.shapes++;
    }

    @Override
    public void dashedLines(double[] coords, int length, LineMode mode, int argb, int factor, int pattern) {
        this.shapes++;
    }

    @Override
    public void points(double[] coords, int length, int argb, float sizePx) {
        this.shapes++;
    }

    @Override
    public void fillRect(double x1, double y1, double x2, double y2, int argb) {
        this.shapes++;
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        this.texts.add(text);
    }

    @Override
    public void centeredText(String text, int x, int y, int argb) {
        this.texts.add(text);
    }

    @Override
    public int textWidth(String text) {
        return text.length() * 6;
    }

    @Override
    public void styledText(String text, float x, float y, int argb, Typeface typeface) {
        this.texts.add(text);
    }

    @Override
    public float styledTextWidth(String text, Typeface typeface) {
        return text.length() * 6;
    }

    @Override
    public void texture(ResourceLocation texture, double x, double y, double width, double height, Tint tint) {
        this.shapes++;
    }

    @Override
    public void textureRegion(ResourceLocation texture, double left, double top, double width, double height,
                              double u, double v, double uWidth, double vHeight, float rotation, int textureWidth,
                              int textureHeight) {
        this.shapes++;
    }

    @Override
    public int[] textureSize(ResourceLocation texture) {
        return new int[]{0, 0};
    }

    @Override
    public void strokeLine(float x1, float y1, float x2, float y2, float width, int argb) {
        this.shapes++;
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb) {
        this.shapes++;
    }

    @Override
    public void flush() {
    }
}
