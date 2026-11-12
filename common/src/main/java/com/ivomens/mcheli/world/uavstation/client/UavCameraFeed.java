package com.ivomens.mcheli.world.uavstation.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import com.ivomens.mcheli.vehicle.client.camera.ViewEntityDummy;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The live camera feed of a UAV, for the station screen. While a screen asks for a client-loaded UAV, the
 * world is rendered from the UAV's onboard camera into an off-screen render target at the start of every
 * other frame: the main render target is swapped for the feed target and the camera entity for the view
 * stand-in, positioned at the UAV.
 *
 * <p>One per client, owned by the client session; only the render thread touches it. One shared
 * full-screen target is enough, since only one station screen is open at a time.
 */
public final class UavCameraFeed {

    public static final float FEED_FOV = 90.0F;
    /** A screen that stops asking for frames for this long has closed. */
    private static final int REQUEST_TIMEOUT_FRAMES = 2;

    private final VehicleCamera camera;
    @Nullable
    private TextureTarget buffer;
    @Nullable
    private AircraftEntity pendingUav;
    private int pendingFrame = -10;
    private int frameCounter;
    private boolean ready;
    /** True while the level is being rendered into the feed; hides the hand and overrides the FOV. */
    private volatile boolean rendering;

    public UavCameraFeed(VehicleCamera camera) {
        this.camera = camera;
    }

    /** Whether the level is being rendered into the feed right now. */
    public boolean isRendering() {
        return this.rendering;
    }

    /** A screen shows the UAV this frame; the feed keeps rendering it while it is asked for. */
    void request(AircraftEntity uav) {
        if (this.pendingUav != uav) this.ready = false;
        this.pendingUav = uav;
        this.pendingFrame = this.frameCounter;
    }

    /** The screen shows no UAV feed: the last frame is stale. */
    void dropFrame() {
        this.ready = false;
    }

    /** The screen closed. */
    void stop() {
        this.pendingUav = null;
        this.ready = false;
    }

    /** The rendered feed, or null while there is no fresh frame. */
    @Nullable
    TextureTarget frame() {
        return this.ready ? this.buffer : null;
    }

    /** Renders the pending feed, if a station screen asked for one during the last frames. */
    public void onRenderFrameStart(DeltaTracker deltaTracker) {
        this.frameCounter++;
        if (this.pendingUav == null || this.frameCounter - this.pendingFrame > REQUEST_TIMEOUT_FRAMES) {
            this.pendingUav = null;
            return;
        }
        // Rendering the level twice per frame is expensive, so the feed refreshes every other frame.
        if ((this.frameCounter & 1) != 0 && this.ready) return;
        try {
            render(Minecraft.getInstance(), this.pendingUav, deltaTracker);
        } catch (Throwable t) {
            // A render hiccup must never take down the GUI.
            this.ready = false;
            this.pendingUav = null;
        }
    }

    private void render(Minecraft mc, AircraftEntity uav, DeltaTracker deltaTracker) {
        if (mc.level == null || mc.player == null || uav.isRemoved() || uav.level() != mc.level) {
            this.ready = false;
            return;
        }
        TextureTarget target = resizedBuffer(mc);
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        ViewEntityDummy dummy = this.camera.viewDummy(mc.level);
        if (dummy == null) return;
        double camX = uav.xOld + (uav.getX() - uav.xOld) * partialTicks;
        double camY = uav.yOld + (uav.getY() - uav.yOld) * partialTicks + uav.getEyeHeight();
        double camZ = uav.zOld + (uav.getZ() - uav.zOld) * partialTicks;
        float yaw = uav.getYRot();
        float pitch = uav.getXRot();
        this.camera.placeViewDummy(camX, camY, camZ);
        dummy.setYRot(yaw);
        dummy.setXRot(pitch);
        dummy.yRotO = yaw;
        dummy.xRotO = pitch;
        dummy.setYHeadRot(yaw);

        Entity prevCam = mc.cameraEntity;
        CameraType prevType = mc.options.getCameraType();
        RenderTarget main = mc.mainRenderTarget;
        try {
            this.rendering = true;
            mc.cameraEntity = dummy;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.mainRenderTarget = target;
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            mc.gameRenderer.renderLevel(deltaTracker);
            this.ready = true;
        } finally {
            this.rendering = false;
            mc.mainRenderTarget = main;
            main.bindWrite(true);
            mc.cameraEntity = prevCam;
            mc.options.setCameraType(prevType);
        }
    }

    private TextureTarget resizedBuffer(Minecraft mc) {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (this.buffer == null) {
            this.buffer = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            this.buffer.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        } else if (this.buffer.width != width || this.buffer.height != height) {
            this.buffer.resize(width, height, Minecraft.ON_OSX);
        }
        return this.buffer;
    }
}
