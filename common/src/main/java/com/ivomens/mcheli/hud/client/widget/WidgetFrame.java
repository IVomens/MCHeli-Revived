package com.ivomens.mcheli.hud.client.widget;

import com.ivomens.mcheli.api.client.HudWidgetContext;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.hud.client.modern.HologramPen;
import com.ivomens.mcheli.hud.client.modern.ModernHud;
import net.minecraft.world.entity.player.Player;

/**
 * The {@link HudWidgetContext} of one frame, reused for every widget and every frame: {@link HudWidgetLayer} sets
 * the frame once and the box before each widget. The pens draw through the modern HUD's {@link HologramPen}, so a
 * widget looks like the HUD around it.
 */
final class WidgetFrame implements HudWidgetContext {

    /** The caption of a readout above its box, and the box's height. */
    private static final float READOUT_LABEL = 12;
    private static final float READOUT_BOX = READOUT_HEIGHT - READOUT_LABEL;

    private HologramPen pen;
    private HudCanvas canvas;
    private Vehicle vehicle;
    private Player viewer;
    private int seat;
    private float partialTick;
    private boolean modern;
    private int color;
    private float scale;
    private int width;
    private int height;
    private boolean drawing;

    /** Starts a frame on {@code canvas}; the pen is made again only when the canvas changed. */
    void begin(HudCanvas canvas, Vehicle vehicle, Player viewer, int seat, float partialTick, HudLayout layout,
               int color, float scale) {
        if (this.canvas != canvas) {
            this.canvas = canvas;
            this.pen = new HologramPen(canvas);
        }
        this.vehicle = vehicle;
        this.viewer = viewer;
        this.seat = seat;
        this.partialTick = partialTick;
        this.modern = layout != HudLayout.LEGACY;
        this.color = color;
        this.scale = scale;
    }

    HologramPen pen() {
        return this.pen;
    }

    /** Sets the box of the next widget; the pens only draw while {@code drawing}. */
    void place(int width, int height, boolean drawing) {
        this.width = width;
        this.height = height;
        this.drawing = drawing;
    }

    /** Ends the frame, so the context keeps neither the vehicle nor the viewer. */
    void end() {
        this.vehicle = null;
        this.viewer = null;
        this.drawing = false;
    }

    @Override
    public Vehicle vehicle() {
        return this.vehicle;
    }

    @Override
    public VehicleKind kind() {
        return this.vehicle.kind();
    }

    @Override
    public Player viewer() {
        return this.viewer;
    }

    @Override
    public int seat() {
        return this.seat;
    }

    @Override
    public float partialTick() {
        return this.partialTick;
    }

    @Override
    public int screenWidth() {
        return this.canvas.width();
    }

    @Override
    public int screenHeight() {
        return this.canvas.height();
    }

    @Override
    public boolean modernHud() {
        return this.modern;
    }

    @Override
    public float hudScale() {
        return this.scale;
    }

    @Override
    public int color() {
        return this.color;
    }

    @Override
    public int cautionColor() {
        return ModernHud.CAUTION;
    }

    @Override
    public int warningColor() {
        return ModernHud.WARNING;
    }

    @Override
    public float opacity() {
        return (this.color >>> 24) / 255F;
    }

    @Override
    public int withAlpha(int argb, float alpha) {
        return HologramPen.withAlpha(argb, Math.clamp(alpha, 0F, 1F));
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
    public void text(String text, float x, float y, int argb) {
        if (this.drawing && text != null && !text.isEmpty()) this.pen.mono(text, x, y, argb);
    }

    @Override
    public void textRight(String text, float right, float y, int argb) {
        if (this.drawing && text != null && !text.isEmpty()) this.pen.monoRight(text, right, y, argb);
    }

    @Override
    public void textCentered(String text, float centerX, float y, int argb) {
        if (this.drawing && text != null && !text.isEmpty()) this.pen.monoCentered(text, centerX, y, argb);
    }

    @Override
    public float textWidth(String text) {
        return text == null ? 0 : this.canvas.styledTextWidth(text, HudCanvas.Typeface.MONO);
    }

    @Override
    public void label(String text, float x, float y, int argb) {
        if (this.drawing && text != null && !text.isEmpty()) this.pen.label(text, x, y, argb);
    }

    @Override
    public float labelWidth(String text) {
        return text == null ? 0 : this.pen.labelWidth(text);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, int argb) {
        if (this.drawing) this.pen.line(x1, y1, x2, y2, argb);
    }

    @Override
    public void box(float x, float y, float width, float height, int argb) {
        if (this.drawing) this.pen.rect(x, y, width, height, argb);
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb) {
        if (this.drawing) this.pen.fill(x, y, width, height, argb);
    }

    @Override
    public void bar(float x, float y, float width, float height, float fraction, int argb) {
        if (this.drawing) this.pen.bar(x, y, width, height, fraction, argb);
    }

    @Override
    public void readout(float x, float y, float width, String label, String value, int argb) {
        if (!this.drawing) return;
        label(label, x, y, HologramPen.withAlpha(argb, 0.8F));
        this.pen.readout(x, y + READOUT_LABEL, width, READOUT_BOX, value == null ? "" : value, argb);
    }
}
