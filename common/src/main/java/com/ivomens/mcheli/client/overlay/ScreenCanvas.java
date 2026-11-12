package com.ivomens.mcheli.client.overlay;

import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * The {@link HudCanvas} of the game window, on the {@link GuiGraphics} of one frame.
 * <p>
 * Pen lines are screen-aligned quads, because core profiles have no wide lines: they are as many physical
 * pixels wide as {@link #lineWidth} says on every driver, stippled lines are cut into dashes, and points are
 * small squares. Each pen call is drawn at once with the position-colour shader. Shapes go into the GUI
 * render type of the frame's buffer, which draws them with the next text or {@link #flush}.
 * <p>
 * Besides the HUD calls it keeps the fixed-function style state the overlays are written against
 * (blending, the shader colour, the bound texture), which {@link HudOverlay} hands them.
 */
public final class ScreenCanvas implements HudCanvas {

    /** Offset of the width and height fields in a PNG file (signature + IHDR chunk header). */
    private static final int PNG_SIZE_OFFSET = 16;
    private static final Style MONO = Style.EMPTY.withFont(Ids.of("hud_mono"));
    private static final Style LABEL = Style.EMPTY.withFont(Ids.of("hud_label"));

    private final GuiGraphics graphics;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Vector3f start = new Vector3f();
    private final Vector3f end = new Vector3f();
    private final float[] savedColor = new float[4];
    private float lineWidthPx = 1.0F;
    /** Set while a blend function other than the default (additive or invert) is active. */
    private boolean customBlend;

    public ScreenCanvas(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics graphics() {
        return this.graphics;
    }

    private Font font() {
        return this.minecraft.font;
    }

    /** The window's GUI scale, unrounded, which turns physical pixels into GUI pixels. */
    private double windowScale() {
        double scale = this.minecraft.getWindow().getGuiScale();
        return scale <= 0 ? 1.0 : scale;
    }

    @Override
    public int width() {
        return this.minecraft.getWindow().getGuiScaledWidth();
    }

    @Override
    public int height() {
        return this.minecraft.getWindow().getGuiScaledHeight();
    }

    @Override
    public int guiScale() {
        return (int) Math.max(1, Math.round(this.minecraft.getWindow().getGuiScale()));
    }

    // Transform

    @Override
    public void pushPose() {
        this.graphics.pose().pushPose();
    }

    @Override
    public void popPose() {
        this.graphics.pose().popPose();
    }

    @Override
    public void translate(double x, double y) {
        this.graphics.pose().translate(x, y, 0.0);
    }

    @Override
    public void scale(float x, float y) {
        this.graphics.pose().scale(x, y, 1.0F);
    }

    @Override
    public void rotate(float degrees) {
        if (degrees != 0.0F) {
            this.graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        }
    }

    @Override
    public void resetTransform() {
        this.graphics.pose().last().pose().identity();
        this.graphics.pose().last().normal().identity();
    }

    // Render state for the older screens

    public void bindTexture(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
    }

    public void color(float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    public void resetColor() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void enableBlend() {
        this.customBlend = false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public void disableBlend() {
        this.customBlend = false;
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** {@code glBlendFunc(GL_ONE, GL_ONE)}. */
    public void additiveBlend() {
        this.customBlend = true;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }

    /** {@code glBlendFunc(ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR)}: inverts what is underneath. */
    public void invertBlend() {
        this.customBlend = true;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
    }

    /** Width of pen lines in physical pixels, like {@code glLineWidth}. */
    public void lineWidth(float px) {
        this.lineWidthPx = Math.max(1.0F, px);
    }

    public float lineWidth() {
        return this.lineWidthPx;
    }

    // Pen

    @Override
    public void lines(double[] coords, int length, LineMode mode, int argb) {
        drawLines(coords, length - (length & 1), mode, argb, 0, 0);
    }

    @Override
    public void dashedLines(double[] coords, int length, LineMode mode, int argb, int factor, int pattern) {
        drawLines(coords, length - (length & 1), mode, argb, Math.max(1, factor), pattern & 0xFFFF);
    }

    private void drawLines(double[] c, int length, LineMode mode, int argb, int factor, int pattern) {
        if (length < 2) return;
        Matrix4f matrix = this.graphics.pose().last().pose();
        float half = (float) (this.lineWidthPx / windowScale() / 2.0);
        BufferBuilder buffer = beginShapes(VertexFormat.Mode.QUADS);
        int points = length / 2;
        boolean any = false;
        if (mode == LineMode.LINES) {
            for (int i = 0; i + 1 < points; i += 2) {
                any |= segment(buffer, matrix, c[i * 2], c[i * 2 + 1], c[i * 2 + 2], c[i * 2 + 3], half, argb, factor, pattern);
            }
        } else {
            for (int i = 0; i + 1 < points; i++) {
                any |= segment(buffer, matrix, c[i * 2], c[i * 2 + 1], c[i * 2 + 2], c[i * 2 + 3], half, argb, factor, pattern);
            }
            if (mode == LineMode.LINE_LOOP && points > 2) {
                any |= segment(buffer, matrix, c[(points - 1) * 2], c[(points - 1) * 2 + 1], c[0], c[1], half, argb,
                        factor, pattern);
            }
        }
        if (any) {
            upload(buffer);
        } else {
            buffer.build();
        }
        endShapes();
    }

    private boolean segment(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2, double y2,
                            float half, int argb, int factor, int pattern) {
        Vector3f a = matrix.transformPosition((float) x1, (float) y1, 0.0F, this.start);
        Vector3f b = matrix.transformPosition((float) x2, (float) y2, 0.0F, this.end);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) return false;
        float ux = dx / length;
        float uy = dy / length;
        if (factor <= 0 || pattern == 0xFFFF) {
            quad(buffer, a.x - ux * half, a.y - uy * half, b.x + ux * half, b.y + uy * half, a.z, ux, uy, half, argb);
            return true;
        }
        if (pattern == 0) return false;
        // Stipple: one pattern bit covers 'factor' physical pixels.
        float bitLength = (float) (factor / windowScale());
        boolean drawn = false;
        float position = 0.0F;
        int bit = 0;
        while (position < length) {
            float next = Math.min(length, position + bitLength);
            if ((pattern >> (bit & 15) & 1) != 0) {
                quad(buffer, a.x + ux * position, a.y + uy * position, a.x + ux * next, a.y + uy * next, a.z, ux, uy,
                        half, argb);
                drawn = true;
            }
            position = next;
            bit++;
        }
        return drawn;
    }

    private static void quad(BufferBuilder buffer, float ax, float ay, float bx, float by, float z, float ux, float uy,
                             float half, int argb) {
        float nx = -uy * half;
        float ny = ux * half;
        buffer.addVertex(ax + nx, ay + ny, z).setColor(argb);
        buffer.addVertex(bx + nx, by + ny, z).setColor(argb);
        buffer.addVertex(bx - nx, by - ny, z).setColor(argb);
        buffer.addVertex(ax - nx, ay - ny, z).setColor(argb);
    }

    /** A filled polygon of the first {@code length} values of {@code coords}, for the older screens. */
    public void polygon(double[] coords, int length, int argb, VertexFormat.Mode mode) {
        Matrix4f matrix = this.graphics.pose().last().pose();
        BufferBuilder buffer = beginShapes(mode);
        for (int i = 0; i + 1 < length; i += 2) {
            buffer.addVertex(matrix, (float) coords[i], (float) coords[i + 1], 0.0F).setColor(argb);
        }
        upload(buffer);
        endShapes();
    }

    @Override
    public void points(double[] coords, int length, int argb, float sizePx) {
        int even = length - (length & 1);
        if (even < 2) return;
        Matrix4f matrix = this.graphics.pose().last().pose();
        float half = (float) (Math.max(1.0F, sizePx) / windowScale() / 2.0);
        BufferBuilder buffer = beginShapes(VertexFormat.Mode.QUADS);
        for (int i = 0; i + 1 < even; i += 2) {
            Vector3f p = matrix.transformPosition((float) coords[i], (float) coords[i + 1], 0.0F, this.start);
            buffer.addVertex(p.x - half, p.y + half, p.z).setColor(argb);
            buffer.addVertex(p.x + half, p.y + half, p.z).setColor(argb);
            buffer.addVertex(p.x + half, p.y - half, p.z).setColor(argb);
            buffer.addVertex(p.x - half, p.y - half, p.z).setColor(argb);
        }
        upload(buffer);
        endShapes();
    }

    /** The current colour does not tint it. */
    @Override
    public void fillRect(double x1, double y1, double x2, double y2, int argb) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double top = Math.min(y1, y2);
        double bottom = Math.max(y1, y2);
        Matrix4f matrix = this.graphics.pose().last().pose();
        BufferBuilder buffer = beginShapes(VertexFormat.Mode.QUADS);
        buffer.addVertex(matrix, (float) left, (float) bottom, 0).setColor(argb);
        buffer.addVertex(matrix, (float) right, (float) bottom, 0).setColor(argb);
        buffer.addVertex(matrix, (float) right, (float) top, 0).setColor(argb);
        buffer.addVertex(matrix, (float) left, (float) top, 0).setColor(argb);
        upload(buffer);
        endShapes();
    }

    /** Opens a pen draw: white shader colour (the vertices carry the colour), blending, no depth test or culling. */
    private BufferBuilder beginShapes(VertexFormat.Mode mode) {
        float[] color = RenderSystem.getShaderColor();
        System.arraycopy(color, 0, this.savedColor, 0, 4);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (!this.customBlend) RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION_COLOR);
    }

    private void endShapes() {
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(this.savedColor[0], this.savedColor[1], this.savedColor[2], this.savedColor[3]);
    }

    private static void upload(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // Textures

    @Override
    public void texture(ResourceLocation texture, double x, double y, double width, double height, Tint tint) {
        enableBlend();
        color(tint.red(), tint.green(), tint.blue(), tint.alpha());
        bindTexture(texture);
        texturedQuad(x, y, width, height, 0, 0, 1, 1);
        resetColor();
        disableBlend();
    }

    @Override
    public void textureRegion(ResourceLocation texture, double left, double top, double width, double height, double u,
                              double v, double uWidth, double vHeight, float rotation, int textureWidth,
                              int textureHeight) {
        enableBlend();
        resetColor();
        bindTexture(texture);
        rotatedRegion(left, top, width, height, u, v, uWidth, vHeight, rotation, textureWidth, textureHeight);
    }

    /** A region given in texels, rotated about the centre of the rectangle, with the bound texture. */
    public void rotatedRegion(double left, double top, double width, double height, double u, double v, double uWidth,
                              double vHeight, float rotation, double textureWidth, double textureHeight) {
        this.graphics.pose().pushPose();
        this.graphics.pose().translate(left + width / 2.0, top + height / 2.0, 0.0);
        if (rotation != 0.0F) this.graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        double fx = 1.0 / textureWidth;
        double fy = 1.0 / textureHeight;
        texturedQuad(-width / 2.0, -height / 2.0, width, height, u * fx, v * fy, (u + uWidth) * fx,
                (v + vHeight) * fy);
        this.graphics.pose().popPose();
    }

    /** A quad of the bound texture, tinted by the shader colour, with normalised texture coordinates. */
    public void texturedQuad(double x, double y, double width, double height, double u0, double v0, double u1,
                             double v1) {
        Matrix4f matrix = this.graphics.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, (float) x, (float) (y + height), 0).setUv((float) u0, (float) v1);
        buffer.addVertex(matrix, (float) (x + width), (float) (y + height), 0).setUv((float) u1, (float) v1);
        buffer.addVertex(matrix, (float) (x + width), (float) y, 0).setUv((float) u1, (float) v0);
        buffer.addVertex(matrix, (float) x, (float) y, 0).setUv((float) u0, (float) v0);
        upload(buffer);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** Reads width and height from the PNG header without uploading the image. */
    @Override
    public int[] textureSize(ResourceLocation texture) {
        Optional<Resource> resource = this.minecraft.getResourceManager().getResource(texture);
        if (resource.isEmpty()) return new int[]{0, 0};
        try (InputStream in = resource.get().open(); DataInputStream data = new DataInputStream(in)) {
            data.skipNBytes(PNG_SIZE_OFFSET);
            return new int[]{data.readInt(), data.readInt()};
        } catch (IOException e) {
            return new int[]{0, 0};
        }
    }

    // Text

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        if (text == null) return;
        this.graphics.drawString(font(), text, x, y, argb, shadow);
    }

    @Override
    public void centeredText(String text, int x, int y, int argb) {
        if (text == null) return;
        this.graphics.drawString(font(), text, x - font().width(text) / 2, y, argb, true);
    }

    @Override
    public int textWidth(String text) {
        return font().width(text);
    }

    @Override
    public void styledText(String text, float x, float y, int argb, HudCanvas.Typeface typeface) {
        this.graphics.pose().pushPose();
        this.graphics.pose().translate(x, y, 0);
        this.graphics.drawString(font(), styled(text, typeface), 0, 0, argb, false);
        this.graphics.pose().popPose();
    }

    @Override
    public float styledTextWidth(String text, HudCanvas.Typeface typeface) {
        return font().width(styled(text, typeface));
    }

    private static Component styled(String text, HudCanvas.Typeface typeface) {
        return switch (typeface) {
            case DEFAULT -> Component.literal(text);
            case MONO -> Component.literal(text).withStyle(MONO);
            case LABEL -> Component.literal(text).withStyle(LABEL);
        };
    }

    // Shapes

    /** Wound the same way as {@link #fill}, so the GUI render type does not cull the quad. */
    @Override
    public void strokeLine(float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) return;
        float nx = dy / length * width / 2;
        float ny = -dx / length * width / 2;
        Matrix4f matrix = this.graphics.pose().last().pose();
        VertexConsumer vertices = this.graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(argb);
        vertices.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(argb);
        vertices.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(argb);
        vertices.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(argb);
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb) {
        Matrix4f matrix = this.graphics.pose().last().pose();
        VertexConsumer vertices = this.graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.addVertex(matrix, x, y, 0).setColor(argb);
        vertices.addVertex(matrix, x, y + height, 0).setColor(argb);
        vertices.addVertex(matrix, x + width, y + height, 0).setColor(argb);
        vertices.addVertex(matrix, x + width, y, 0).setColor(argb);
    }

    @Override
    public void flush() {
        this.graphics.flush();
    }
}
