package com.ivomens.mcheli.client.overlay;

import net.minecraft.resources.ResourceLocation;

/**
 * Where every HUD draws: the scripted HUDs, the instruments and the modern HUD. Coordinates are GUI pixels
 * through the current transform. In game this is the screen; tests record the calls instead.
 * <p>
 * The primitives come in two kinds, because the two HUD styles were drawn differently and must look the
 * same as they always did:
 * <ul>
 *     <li>the <em>pen</em> ({@link #lines}, {@link #dashedLines}, {@link #points}, {@link #fillRect}) draws
 *     each call at once, with lines a fixed number of physical pixels wide whatever the transform, as OpenGL
 *     lines were for the scripted HUDs;</li>
 *     <li>the <em>shapes</em> ({@link #strokeLine}, {@link #fill}) are batched, with widths in canvas units
 *     that scale with the transform, as the modern HUD draws them; text and {@link #flush} draw the batch.</li>
 * </ul>
 */
public interface HudCanvas {

    /** How {@link #lines} joins the points it is given, as the OpenGL primitive of the same name did. */
    enum LineMode {
        /** Separate segments between pairs of points. */
        LINES,
        /** One line through every point. */
        LINE_STRIP,
        /** One line through every point and back to the first. */
        LINE_LOOP
    }

    /** The font of {@link #styledText}. */
    enum Typeface {
        /** The game's font. */
        DEFAULT,
        /** The modern HUD's monospaced digits. */
        MONO,
        /** The modern HUD's label font. */
        LABEL
    }

    /** A colour that multiplies a texture's own, channels from 0 to 1. */
    record Tint(float red, float green, float blue, float alpha) {

        public static final Tint WHITE = new Tint(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Width in GUI pixels. */
    int width();

    /** Height in GUI pixels. */
    int height();

    /** Physical pixels per GUI pixel, rounded, at least 1. */
    int guiScale();

    void pushPose();

    void popPose();

    void translate(double x, double y);

    void scale(float x, float y);

    /** Rotates what is drawn after it by {@code degrees} clockwise on screen. */
    void rotate(float degrees);

    /** Makes the transform the identity, inside a {@link #pushPose}, for a HUD that lays itself out alone. */
    void resetTransform();

    /** Lines through the first {@code length} values of {@code coords} (x0, y0, x1, y1, ...). */
    void lines(double[] coords, int length, LineMode mode, int argb);

    /** Dashed lines: {@code factor} physical pixels for each bit of the 16-bit {@code pattern}, lowest first. */
    void dashedLines(double[] coords, int length, LineMode mode, int argb, int factor, int pattern);

    /** Square dots {@code sizePx} physical pixels wide at the first {@code length} values of {@code coords}. */
    void points(double[] coords, int length, int argb, float sizePx);

    /** A filled rectangle between two corners given in any order. */
    void fillRect(double x1, double y1, double x2, double y2, int argb);

    /** Text in the game font; the scripted HUDs write it with a shadow. */
    void text(String text, int x, int y, int argb, boolean shadow);

    /** Text in the game font with a shadow, centred on {@code x}. */
    void centeredText(String text, int x, int y, int argb);

    int textWidth(String text);

    /** Text in one of the HUD fonts, without a shadow, at a point that need not be a whole pixel. */
    void styledText(String text, float x, float y, int argb, Typeface typeface);

    float styledTextWidth(String text, Typeface typeface);

    /** A whole texture stretched over a rectangle, tinted, with default blending. */
    void texture(ResourceLocation texture, double x, double y, double width, double height, Tint tint);

    /**
     * A region of a texture, in texels of a texture {@code textureWidth} by {@code textureHeight}, rotated by
     * {@code rotation} degrees about the centre of the rectangle; drawn with default blending in white, which
     * stays set for what the scripted HUD draws next.
     */
    void textureRegion(ResourceLocation texture, double left, double top, double width, double height, double u,
                       double v, double uWidth, double vHeight, float rotation, int textureWidth, int textureHeight);

    /** Width and height of a texture in texels, or zeros when it cannot be read. */
    int[] textureSize(ResourceLocation texture);

    /** A batched line {@code width} canvas units wide. */
    void strokeLine(float x1, float y1, float x2, float y2, float width, int argb);

    /** A batched filled rectangle. */
    void fill(float x, float y, float width, float height, int argb);

    /** Draws the batched shapes and text. */
    void flush();
}
