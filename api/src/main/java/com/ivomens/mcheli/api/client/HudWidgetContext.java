package com.ivomens.mcheli.api.client;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.entity.player.Player;

/**
 * What a {@link HudWidget} sees of the frame it draws in, read-only: the vehicle and the viewer, the HUD's
 * settings, and pens that draw in the modern HUD's style (thin strokes, its monospaced and label fonts, boxed
 * readouts and bars).
 *
 * <p>All positions and sizes are in HUD units, relative to the widget's box: (0, 0) is its top left corner and
 * ({@link #width()}, {@link #height()}) its bottom right. Colours are ARGB, {@code 0xAARRGGBB}; a colour with an
 * alpha of 0 is invisible, so write {@code 0xFF...} for an opaque one, or start from {@link #color()}.
 *
 * <p><b>Side and thread.</b> Client render thread, and only while MCHeli calls the widget's {@code isShown} or
 * {@code render}: the context is reused for the next widget afterwards. The drawing methods work only in
 * {@code render}.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface HudWidgetContext {

    /** The height of one line of {@link #text} or {@link #label}, in HUD units. */
    int LINE_HEIGHT = 12;

    /** The height of a {@link #readout}: its label line and the box below it, in HUD units. */
    int READOUT_HEIGHT = 28;

    // The frame

    /** The vehicle whose HUD is drawn: the one the viewer sits in or flies remotely. */
    Vehicle vehicle();

    /** The vehicle's kind, the same as {@code vehicle().kind()}. */
    VehicleKind kind();

    /** The player looking at the HUD, the client's own player. */
    Player viewer();

    /**
     * The viewer's seat in the vehicle: 0 for the pilot, 1 and up for the passenger seats as in
     * {@link com.ivomens.mcheli.api.vehicle.Crew}, or -1 when the viewer is in no seat of it (a UAV operator).
     */
    int seat();

    /** The fraction of the current tick that has passed, from 0 to 1, to interpolate moving values. */
    float partialTick();

    /** The width of the game window in GUI pixels. */
    int screenWidth();

    /** The height of the game window in GUI pixels. */
    int screenHeight();

    // The HUD's settings

    /**
     * Whether the modern HUD is drawn ({@code HudStyle = modern}); false for the legacy scripted HUD, which is
     * also used while the viewer steers a TV missile.
     */
    boolean modernHud();

    /** The {@code HudScale} setting, 0.5 to 2: how much larger than at the default size the HUD is drawn. */
    float hudScale();

    /** The HUD colour the player chose ({@code HudColor}), ARGB. Draw in it to match the HUD. */
    int color();

    /** The modern HUD's colour for cautions (low fuel, reloading), ARGB. */
    int cautionColor();

    /** The modern HUD's colour for warnings (incoming missile, failed engine, low hull), ARGB. */
    int warningColor();

    /** The opacity of the HUD colour, from 0 to 1: its alpha channel. */
    float opacity();

    /** The colour {@code argb} with its alpha replaced by {@code alpha} (0 to 1), for dimmed text or fills. */
    int withAlpha(int argb, float alpha);

    // The widget's box

    /** The width of the widget's box, its {@link HudWidget#width()}. */
    int width();

    /** The height of the widget's box, its {@link HudWidget#height()}. */
    int height();

    // Pens

    /** Text in the HUD's monospaced font with its top left corner at (x, y). Null or empty draws nothing. */
    void text(String text, float x, float y, int argb);

    /** Text in the HUD's monospaced font ending at {@code right}. */
    void textRight(String text, float right, float y, int argb);

    /** Text in the HUD's monospaced font centred on {@code centerX}. */
    void textCentered(String text, float centerX, float y, int argb);

    /** The width of {@code text} in the monospaced font, in HUD units. */
    float textWidth(String text);

    /** Text in the HUD's label font (the one of its captions and alerts) with its top left corner at (x, y). */
    void label(String text, float x, float y, int argb);

    /** The width of {@code text} in the label font, in HUD units. */
    float labelWidth(String text);

    /** A straight line in the HUD's stroke width. */
    void line(float x1, float y1, float x2, float y2, int argb);

    /** The outline of a rectangle in the HUD's stroke width. */
    void box(float x, float y, float width, float height, int argb);

    /** A filled rectangle. */
    void fill(float x, float y, float width, float height, int argb);

    /**
     * A gauge as the HUD draws its fuel and throttle bars: an outline, filled from the left by {@code fraction}
     * (clamped to 0 to 1).
     */
    void bar(float x, float y, float width, float height, float fraction, int argb);

    /**
     * A number with a caption, as the HUD shows its readouts: {@code label} in the label font, and below it
     * {@code value} centred in an outlined box on a faint dark backing. It is {@code width} wide and
     * {@link #READOUT_HEIGHT} high.
     */
    void readout(float x, float y, float width, String label, String value, int argb);
}
