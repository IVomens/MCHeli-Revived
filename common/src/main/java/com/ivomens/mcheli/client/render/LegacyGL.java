package com.ivomens.mcheli.client.render;

import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fixed-function style drawing on top of the vanilla render pipeline.
 * <p>
 * MCHeli renderers (and addon renderers written in the same style) draw with an immediate-mode
 * vocabulary: push/translate/rotate/pop, a "bound" texture, a current colour, blend and lighting
 * switches. This class maps that vocabulary onto the {@link PoseStack} and {@link MultiBufferSource}
 * that vanilla hands to every renderer, so no raw GL state or GL objects are touched and everything
 * works on every GL profile Minecraft supports.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * LegacyGL.begin(poseStack, buffers, packedLight);
 * try {
 *     LegacyGL.pushMatrix();
 *     LegacyGL.translate(0, 1, 0);
 *     LegacyGL.rotate(yaw, 0, -1, 0);
 *     LegacyGL.bindTexture(texture);
 *     model.renderAll();              // baked models emit through emitBaked()
 *     LegacyGL.popMatrix();
 * } finally {
 *     LegacyGL.end();
 * }
 * }</pre>
 * Entity renderers extending {@code BaseEntityRenderer} already run inside a frame.
 *
 * <h2>Rules</h2>
 * <ul>
 *     <li>Render thread only; every call except {@link #begin}/{@link #active} needs an open frame.</li>
 *     <li>Frames nest: {@link #begin} opens a frame on top of the current one and {@link #end} returns to
 *     it, so an item renderer called from inside an entity renderer is safe.</li>
 *     <li>{@link #pushMatrix()} saves only the matrix, as {@code glPushMatrix} did. Use
 *     {@link #pushAttrib()} to save colour, texture and switches.</li>
 *     <li>Back-face culling is on by default. Vehicle models rely on it: the pilot's camera sits inside
 *     the airframe, and drawing back faces would put the fuselage interior over the view.</li>
 *     <li>The render type of textured geometry is chosen from the state when a buffer is requested:
 *     additive, translucent without depth write, translucent (blend on or alpha below 1) or cutout.</li>
 * </ul>
 */
public final class LegacyGL {

    /** Floats per vertex in baked model arrays: x, y, z, u, v, nx, ny, nz. */
    public static final int BAKED_STRIDE = 8;

    public static final ResourceLocation WHITE = Ids.of("textures/misc/white.png");

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float MIN_LINE_LENGTH = 1.0E-6F;

    /** The open frames, innermost first: the render thread's draw calls go to the first. */
    private static final Deque<RenderFrame> OPEN_FRAMES = new ArrayDeque<>();
    private static final Deque<RenderFrame> FRAME_POOL = new ArrayDeque<>();
    private static final Quaternionf SCRATCH_ROTATION = new Quaternionf();

    private LegacyGL() {
    }

    // Frames

    /** Opens a render frame on the given pose stack and buffers. Must be paired with {@link #end()}. */
    public static void begin(PoseStack pose, MultiBufferSource buffers, int packedLight) {
        begin(pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
    }

    public static void begin(PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        RenderFrame opened = FRAME_POOL.poll();
        if (opened == null) {
            opened = new RenderFrame();
        }
        opened.reset(pose, buffers, packedLight, packedOverlay);
        OPEN_FRAMES.push(opened);
    }

    /** Closes the current frame and restores the one that was open before it, if any. */
    public static void end() {
        RenderFrame closed = OPEN_FRAMES.poll();
        if (closed != null) {
            closed.release();
            FRAME_POOL.push(closed);
        }
    }

    public static boolean active() {
        return !OPEN_FRAMES.isEmpty();
    }

    private static RenderFrame frame() {
        RenderFrame current = OPEN_FRAMES.peek();
        if (current == null) {
            throw new IllegalStateException("LegacyGL used outside of begin()/end()");
        }
        return current;
    }

    public static PoseStack pose() {
        return frame().pose;
    }

    public static MultiBufferSource buffers() {
        return frame().buffers;
    }

    // Matrix stack

    public static void pushMatrix() {
        frame().pose.pushPose();
    }

    public static void popMatrix() {
        frame().pose.popPose();
    }

    public static void translate(double x, double y, double z) {
        frame().pose.translate(x, y, z);
    }

    public static void scale(float x, float y, float z) {
        frame().pose.scale(x, y, z);
    }

    public static void scale(double x, double y, double z) {
        frame().pose.scale((float) x, (float) y, (float) z);
    }

    /** Same semantics as {@code glRotatef(angleDegrees, x, y, z)}; a zero angle costs nothing. */
    public static void rotate(float angleDegrees, float x, float y, float z) {
        if (angleDegrees == 0.0F) {
            return;
        }
        frame().pose.mulPose(rotation(angleDegrees, x, y, z, SCRATCH_ROTATION));
    }

    public static void rotate(double angleDegrees, double x, double y, double z) {
        rotate((float) angleDegrees, (float) x, (float) y, (float) z);
    }

    /**
     * Rotates about an axis through {@code (pivotX, pivotY, pivotZ)}; the same as
     * {@code translate(pivot); rotate(...); translate(-pivot)} in a single matrix operation.
     */
    public static void rotateAround(float angleDegrees, float axisX, float axisY, float axisZ,
                                    double pivotX, double pivotY, double pivotZ) {
        if (angleDegrees == 0.0F) {
            return;
        }
        frame().pose.rotateAround(rotation(angleDegrees, axisX, axisY, axisZ, SCRATCH_ROTATION),
                (float) pivotX, (float) pivotY, (float) pivotZ);
    }

    /**
     * The rotation {@link #rotate} applies, written to {@code dest} (the pose stack copies the shared
     * scratch one). Axis-aligned rotations only use the sign of the axis, like fixed-function GL.
     */
    public static Quaternionf rotation(float angleDegrees, float x, float y, float z, Quaternionf dest) {
        float radians = angleDegrees * DEG_TO_RAD;
        if (x == 0.0F && z == 0.0F) {
            return dest.rotationY(radians * Math.signum(y));
        }
        if (y == 0.0F && z == 0.0F) {
            return dest.rotationX(radians * Math.signum(x));
        }
        if (x == 0.0F && y == 0.0F) {
            return dest.rotationZ(radians * Math.signum(z));
        }
        return dest.rotationAxis(radians, x, y, z);
    }

    /**
     * Turns the current pose to face the camera, matching the legacy billboard
     * {@code rotate(-playerViewY, 0, 1, 0); rotate(playerViewX, 1, 0, 0)}, so the usual follow-up
     * {@code scale(-s, -s, s)} still produces upright text and sprites.
     */
    public static void rotateToCamera() {
        PoseStack pose = frame().pose;
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
    }

    // State

    public static void bindTexture(ResourceLocation texture) {
        frame().state.setTexture(texture == null ? WHITE : texture);
    }

    public static ResourceLocation boundTexture() {
        return frame().state.texture;
    }

    public static void color(float r, float g, float b, float a) {
        frame().state.setColor(r, g, b, a);
    }

    public static void color(float r, float g, float b) {
        color(r, g, b, 1.0F);
    }

    public static void resetColor() {
        color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void enableBlend() {
        frame().state.setBlend(true);
    }

    public static void disableBlend() {
        frame().state.setBlend(false);
    }

    /** Additive blending ({@code GL_SRC_ALPHA, GL_ONE}) for glows, flames and lasers. */
    public static void additiveBlend(boolean additive) {
        frame().state.setAdditive(additive);
    }

    public static void enableLighting() {
        frame().state.setFullBright(false);
    }

    /** As in the old pipeline, "lighting off" means the geometry is drawn at full brightness. */
    public static void disableLighting() {
        frame().state.setFullBright(true);
    }

    public static boolean isLightingEnabled() {
        return !frame().state.fullBright;
    }

    public static void enableTexture() {
        frame().state.setTextured(true);
    }

    /** Textured buffers then sample a plain white texture, so only the colour shows. */
    public static void disableTexture() {
        frame().state.setTextured(false);
    }

    /** When false, textured geometry is drawn translucent without writing depth. */
    public static void depthMask(boolean write) {
        frame().state.setDepthMask(write);
    }

    public static void enableCull() {
        frame().state.setCull(true);
    }

    public static void disableCull() {
        frame().state.setCull(false);
    }

    /** Saves colour, texture, blend, lighting, depth mask and cull state, like {@code glPushAttrib}. */
    public static void pushAttrib() {
        RenderFrame current = frame();
        RenderState saved = current.statePool.poll();
        if (saved == null) {
            saved = new RenderState();
        }
        saved.set(current.state);
        current.savedStates.push(saved);
    }

    public static void popAttrib() {
        RenderFrame current = frame();
        RenderState saved = current.savedStates.poll();
        if (saved != null) {
            current.state.set(saved);
            current.statePool.push(saved);
        }
    }

    /** The packed light geometry is drawn with: the frame's light, or full bright with lighting off. */
    public static int light() {
        return frame().effectiveLight();
    }

    public static void setLight(int packedLight) {
        frame().light = packedLight;
    }

    public static int overlay() {
        return frame().overlay;
    }

    // Buffers

    /** Consumer for textured quads (NEW_ENTITY format) with the render type the current state asks for. */
    public static VertexConsumer buffer() {
        RenderFrame current = frame();
        return current.buffers.getBuffer(current.state.renderType());
    }

    /** Consumer for coloured line segments in the vanilla line width (every vertex needs a normal). */
    public static VertexConsumer lineBuffer() {
        return frame().buffers.getBuffer(RenderType.lines());
    }

    /** Lines with an explicit width in pixels; {@code noDepth} draws them over everything. */
    public static VertexConsumer lineBuffer(float width, boolean noDepth) {
        return frame().buffers.getBuffer(McheliRenderTypes.lines(width, noDepth));
    }

    /** Untextured coloured quads (POSITION_COLOR). Triangles must repeat their last vertex. */
    public static VertexConsumer colorBuffer(boolean additive, boolean noDepth) {
        return frame().buffers.getBuffer(McheliRenderTypes.color(additive, noDepth));
    }

    /** Untextured coloured quads using the frame's additive switch. */
    public static VertexConsumer colorBuffer() {
        return colorBuffer(frame().state.additive, false);
    }

    // Emission

    /** One POSITION_COLOR vertex through the current pose. */
    public static void vertexColor(VertexConsumer consumer, float x, float y, float z, float r, float g, float b, float a) {
        consumer.addVertex(frame().pose.last(), x, y, z).setColor(r, g, b, a);
    }

    /** One POSITION_COLOR vertex in the current colour. */
    public static void vertexColor(VertexConsumer consumer, float x, float y, float z) {
        RenderFrame current = frame();
        RenderState state = current.state;
        consumer.addVertex(current.pose.last(), x, y, z).setColor(state.r, state.g, state.b, state.a);
    }

    /** One textured vertex through the current pose, colour, light and overlay. */
    public static void vertex(VertexConsumer consumer, float x, float y, float z, float u, float v,
                              float nx, float ny, float nz) {
        RenderFrame current = frame();
        RenderState state = current.state;
        PoseStack.Pose pose = current.pose.last();
        consumer.addVertex(pose, x, y, z)
                .setColor(state.r, state.g, state.b, state.a)
                .setUv(u, v)
                .setOverlay(current.overlay)
                .setLight(current.effectiveLight())
                .setNormal(pose, nx, ny, nz);
    }

    /** Coloured line segment into a consumer from {@link #lineBuffer(float, boolean)}. */
    public static void line(VertexConsumer consumer, double x1, double y1, double z1, double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < MIN_LINE_LENGTH) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        PoseStack.Pose pose = frame().pose.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
    }

    /** Coloured line in the current colour and the vanilla line width. */
    public static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        RenderState state = frame().state;
        line(lineBuffer(), x1, y1, z1, x2, y2, z2, state.r, state.g, state.b, state.a);
    }

    /** Pixels one block covers at the vehicle being drawn; infinite (each frame's default) is full detail. */
    public static void setDetailPixelsPerBlock(float pixelsPerBlock) {
        frame().detailPixelsPerBlock = pixelsPerBlock;
    }

    /**
     * How many model units one screen pixel spans at the current detail level and pose scale; 0
     * when full detail is wanted. Baked groups use it to pick a reduced mesh.
     */
    public static float modelUnitsPerPixel() {
        if (!active() || frame().detailPixelsPerBlock == Float.POSITIVE_INFINITY) {
            return 0.0F;
        }
        float pixelsPerBlock = frame().detailPixelsPerBlock;
        Matrix4f position = frame().pose.last().pose();
        float scale = (float) Math.sqrt(position.m00() * position.m00() + position.m01() * position.m01()
                + position.m02() * position.m02());
        float pixelsPerUnit = pixelsPerBlock * scale;
        return pixelsPerUnit > 0.0F ? 1.0F / pixelsPerUnit : 0.0F;
    }

    /**
     * Emits baked model data ({@value #BAKED_STRIDE} floats per vertex: x, y, z, u, v, nx, ny, nz; every
     * face already stored as a quad) through the current pose, colour, light and texture.
     */
    public static void emitBaked(float[] data, int firstVertex, int vertexCount) {
        if (vertexCount > 0) {
            BakedEmitter.emit(frame(), data, firstVertex, vertexCount);
        }
    }

    /** Frees the vertex blocks kept for meshes drawn the same way every frame (the client left the world). */
    public static void releaseCachedMeshes() {
        EntityVertexWriter.clearCache();
    }

    /** Emits baked model data as wireframe: the edges of each quad's first triangle. */
    public static void emitBakedLines(float[] data, int firstVertex, int vertexCount) {
        if (vertexCount <= 0) {
            return;
        }
        RenderState state = frame().state;
        VertexConsumer consumer = lineBuffer();
        float r = state.r, g = state.g, b = state.b, a = state.a;
        int end = (firstVertex + vertexCount) * BAKED_STRIDE;
        for (int i = firstVertex * BAKED_STRIDE; i + 3 * BAKED_STRIDE <= end; i += 4 * BAKED_STRIDE) {
            int j = i + BAKED_STRIDE;
            int k = i + 2 * BAKED_STRIDE;
            line(consumer, data[i], data[i + 1], data[i + 2], data[j], data[j + 1], data[j + 2], r, g, b, a);
            line(consumer, data[j], data[j + 1], data[j + 2], data[k], data[k + 1], data[k + 2], r, g, b, a);
            line(consumer, data[k], data[k + 1], data[k + 2], data[i], data[i + 1], data[i + 2], r, g, b, a);
        }
    }

    /** Renders a vanilla {@link ModelPart} with the current pose, texture, colour and light. */
    public static void renderModelPart(ModelPart part) {
        RenderFrame current = frame();
        part.render(current.pose, buffer(), current.effectiveLight(), current.overlay, current.state.packedColor());
    }

    /** Draws text at the current pose, like {@code fontRenderer.drawString} inside a world transform. */
    public static void drawString(String text, float x, float y, int argb, boolean seeThrough, int backgroundArgb) {
        RenderFrame current = frame();
        Minecraft.getInstance().font.drawInBatch(text, x, y, argb, false, current.pose.last().pose(), current.buffers,
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, backgroundArgb,
                current.effectiveLight());
    }

    public static int stringWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }
}
