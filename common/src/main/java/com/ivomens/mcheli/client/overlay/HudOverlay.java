package com.ivomens.mcheli.client.overlay;

import com.ivomens.mcheli.client.render.ModTextures;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * Base of every in-game overlay: the vehicle HUDs, the launcher sights, the hit marker, the wrench,
 * range finder, seat and target marker overlays. Overlays are not screens: the MCHeli HUD layer calls
 * {@link #render} every frame, and {@link #drawGui} draws on that frame's {@link #canvas}. The helpers
 * keep the fixed-function style the overlays are written in: a matrix stack, a current colour and
 * blending, a bound texture.
 */
public abstract class HudOverlay {

    protected static final int CIRCLE_SEGMENTS = 32;
    protected static final double[] CIRCLE_SIN = new double[CIRCLE_SEGMENTS];
    protected static final double[] CIRCLE_COS = new double[CIRCLE_SEGMENTS];
    private static final float VANILLA_TEXTURE_SIZE = 256.0F;
    private static final int DIGIT_TEXTURE_SIZE = 16;
    private static final int DIGIT_MINUS_U_OFFSET = 160;

    static {
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / CIRCLE_SEGMENTS;
            CIRCLE_SIN[i] = Math.sin(angle);
            CIRCLE_COS[i] = Math.cos(angle);
        }
    }

    /** Only shakes the screen-noise texture; never feeds gameplay. */
    private final Random noise = new Random();
    protected final Minecraft mc;
    protected final Font font;
    protected int width;
    protected int height;
    protected int centerX;
    protected int centerY;
    /** The GUI scale rounded to a whole number, for line widths in physical pixels. */
    protected int scaleFactor;
    protected float smoothCamPartialTicks;
    /** The graphics of the frame being drawn; only valid inside {@link #drawGui}. */
    protected GuiGraphics graphics;
    /** The canvas of the frame being drawn; only valid inside {@link #drawGui}. */
    protected ScreenCanvas canvas;

    protected HudOverlay(Minecraft minecraft) {
        this.mc = minecraft;
        this.font = minecraft.font;
    }

    /** The window's GUI scale rounded to a whole number, at least 1. */
    public static int guiScale(Minecraft minecraft) {
        return (int) Math.max(1, Math.round(minecraft.getWindow().getGuiScale()));
    }

    public void onTick() {
    }

    public abstract boolean isDrawGui(Player player);

    public abstract void drawGui(Player player, boolean isThirdPersonView);

    /** Draws the overlay for this frame, unless the GUI is hidden. */
    public final void render(GuiGraphics frame, float partialTicks) {
        this.smoothCamPartialTicks = partialTicks;
        this.scaleFactor = guiScale(this.mc);
        if (this.mc.options.hideGui || this.mc.player == null) {
            return;
        }
        this.width = this.mc.getWindow().getGuiScaledWidth();
        this.height = this.mc.getWindow().getGuiScaledHeight();
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        this.graphics = frame;
        this.canvas = new ScreenCanvas(frame);
        frame.pose().pushPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            this.drawGui(this.mc.player, !this.mc.options.getCameraType().isFirstPerson());
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            this.canvas.disableBlend();
            frame.pose().popPose();
            this.graphics = null;
            this.canvas = null;
        }
    }

    protected void lineWidth(float px) {
        this.canvas.lineWidth(px);
    }

    public void pushMatrix() {
        this.canvas.pushPose();
    }

    public void popMatrix() {
        this.canvas.popPose();
    }

    public void scale(float x, float y, float z) {
        this.graphics.pose().scale(x, y, z);
    }

    public void color(float red, float green, float blue, float alpha) {
        this.canvas.color(red, green, blue, alpha);
    }

    public void resetColor() {
        this.canvas.resetColor();
    }

    public void enableBlend() {
        this.canvas.enableBlend();
    }

    public void disableBlend() {
        this.canvas.disableBlend();
    }

    /** Blending that inverts what is underneath, for crosshairs that show on any background. */
    public void invertBlend() {
        this.canvas.invertBlend();
    }

    public void drawRect(int left, int top, int right, int bottom, int color) {
        this.canvas.fillRect(left, top, right, bottom, color);
    }

    /** A region of a 256x256 texture, rotated about the centre of the rectangle. */
    public void drawTexturedModalRectRotate(double left, double top, double width, double height,
                                            double uLeft, double vTop, double uWidth, double vHeight, float rotationDegrees) {
        this.canvas.rotatedRegion(left, top, width, height, uLeft, vTop, uWidth, vHeight, rotationDegrees,
                VANILLA_TEXTURE_SIZE, VANILLA_TEXTURE_SIZE);
    }

    /** A region, in texels, of the bound {@code textureWidth x textureHeight} texture. */
    public void drawTexturedRect(double left, double top, double width, double height,
                                 double uLeft, double vTop, double uWidth, double vHeight, double textureWidth, double textureHeight) {
        this.canvas.texturedQuad(left, top, width, height, uLeft / textureWidth, vTop / textureHeight,
                (uLeft + uWidth) / textureWidth, (vTop + vHeight) / textureHeight);
    }

    /** Line segments between successive pairs of {@code (x, y)} coordinates. */
    public void drawLine(double[] coordinates, int color) {
        this.drawLine(coordinates, color, LineMode.LINES);
    }

    public void drawLine(double[] coordinates, int color, LineMode mode) {
        this.drawLine(coordinates, coordinates.length, color, mode);
    }

    /** Like {@link #drawLine(double[], int, LineMode)} with only the first {@code length} values. */
    public void drawLine(double[] coordinates, int length, int color, LineMode mode) {
        if (length - (length & 1) >= 2) {
            this.canvas.lines(coordinates, length, mode, color);
        }
    }

    /** Filled triangles, three {@code (x, y)} corners each. */
    public void fillTriangles(double[] coordinates, int color) {
        this.canvas.polygon(coordinates, coordinates.length, color, VertexFormat.Mode.TRIANGLES);
    }

    public void drawString(String text, int x, int y, int color) {
        this.canvas.text(text, x, y, color, true);
    }

    public void drawCenteredString(String text, int x, int y, int color) {
        this.canvas.centeredText(text, x, y, color);
    }

    /** Draws digits and minus signs from the seven-segment style {@code digit.png}. */
    public void drawDigit(String digits, int x, int y, int kerningInterval, int color) {
        this.canvas.enableBlend();
        this.canvas.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                (color >>> 24) / 255.0F);
        ModTextures.bind("textures/gui/digit.png");
        for (int i = 0; i < digits.length(); i++) {
            char character = digits.charAt(i);
            if (character >= '0' && character <= '9') {
                this.drawDigitCell(x + kerningInterval * i, y, DIGIT_TEXTURE_SIZE * (character - '0'));
            }
            if (character == '-') {
                this.drawDigitCell(x + kerningInterval * i, y, DIGIT_MINUS_U_OFFSET);
            }
        }
        this.canvas.resetColor();
        this.canvas.disableBlend();
    }

    private void drawDigitCell(int x, int y, int u) {
        this.canvas.texturedQuad(x, y, DIGIT_TEXTURE_SIZE, DIGIT_TEXTURE_SIZE, u / VANILLA_TEXTURE_SIZE, 0.0,
                (u + DIGIT_TEXTURE_SIZE) / VANILLA_TEXTURE_SIZE, DIGIT_TEXTURE_SIZE / VANILLA_TEXTURE_SIZE);
    }

    /** Covers the screen with a texture at a random offset each frame (night vision grain, TV static). */
    protected void drawScreenNoise(String texture, float red, float green, float blue, float alpha) {
        this.canvas.additiveBlend();
        this.canvas.color(red, green, blue, alpha);
        ModTextures.bind(texture);
        this.drawTexturedModalRectRotate(0.0, 0.0, this.width, this.height, this.noise.nextInt(256),
                this.noise.nextInt(256), 256.0, 256.0, 0.0F);
        this.canvas.resetColor();
        this.canvas.disableBlend();
    }
}
