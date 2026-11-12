package com.ivomens.mcheli.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World to screen projection for HUD markers. The view and projection matrices of the last rendered
 * level frame are captured in {@link #capture}; the HUD, drawn later in the same frame, projects
 * world points with exactly what was rendered. One per client, render thread only.
 */
public final class ScreenProjection {

    private final Matrix4f viewProjection = new Matrix4f();
    private final Vector4f scratch = new Vector4f();
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private boolean valid;

    /** Called once the sky has been rendered, with the matrices of this level frame. */
    public void capture(Matrix4f projection, Matrix4f modelView, Camera camera) {
        this.viewProjection.set(projection).mul(modelView);
        Vec3 position = camera.getPosition();
        this.cameraX = position.x;
        this.cameraY = position.y;
        this.cameraZ = position.z;
        this.valid = true;
    }

    /** Whether a level frame has been captured yet. */
    public boolean isValid() {
        return this.valid;
    }

    /** Where the camera of the captured frame was. */
    public Vec3 cameraPosition() {
        return new Vec3(this.cameraX, this.cameraY, this.cameraZ);
    }

    /**
     * Projects a world position into GUI-scaled screen coordinates.
     *
     * @param out receives {x, y, depth (positive in front of the camera)}
     * @return false if the point is behind the camera (out is then undefined)
     */
    public boolean project(double x, double y, double z, double[] out) {
        if (!this.valid) return false;
        Vector4f clip = this.toClipSpace(x, y, z);
        if (clip.w <= 1.0E-4F) return false;
        Minecraft mc = Minecraft.getInstance();
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        out[0] = (ndcX * 0.5 + 0.5) * mc.getWindow().getGuiScaledWidth();
        out[1] = (1.0 - (ndcY * 0.5 + 0.5)) * mc.getWindow().getGuiScaledHeight();
        out[2] = clip.w;
        return true;
    }

    /**
     * Like {@link #project} but never rejects: for points behind the camera the result is the
     * mirrored projection and {@code out[2]} (the clip w) is negative.
     */
    public boolean projectUnclipped(double x, double y, double z, double[] out) {
        if (!this.valid) return false;
        Vector4f clip = this.toClipSpace(x, y, z);
        float w = Math.abs(clip.w) < 1.0E-4F ? 1.0E-4F : clip.w;
        Minecraft mc = Minecraft.getInstance();
        out[0] = (clip.x / w * 0.5 + 0.5) * mc.getWindow().getGuiScaledWidth();
        out[1] = (1.0 - (clip.y / w * 0.5 + 0.5)) * mc.getWindow().getGuiScaledHeight();
        out[2] = clip.w;
        return true;
    }

    /** Pixels (GUI-scaled) per world unit at {@code distance} along the view axis. */
    public double pixelsPerUnit(double distance) {
        if (!this.valid || distance <= 1.0E-4) return 0.0;
        // m11 of the projection is 1/tan(fov/2); the combined matrix keeps it up to rotation, so use
        // the length of its vertical axis.
        Matrix4f m = this.viewProjection;
        double f = Math.sqrt(m.m01() * m.m01() + m.m11() * m.m11() + m.m21() * m.m21());
        return f * Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0 / distance;
    }

    private Vector4f toClipSpace(double x, double y, double z) {
        this.scratch.set((float) (x - this.cameraX), (float) (y - this.cameraY), (float) (z - this.cameraZ), 1.0F);
        return this.viewProjection.transform(this.scratch);
    }
}
