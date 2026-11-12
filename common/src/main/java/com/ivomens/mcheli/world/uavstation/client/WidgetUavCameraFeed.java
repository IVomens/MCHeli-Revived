package com.ivomens.mcheli.world.uavstation.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.overlay.ScreenCanvas;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.world.uavstation.net.UavPreviewMessage;
import com.ivomens.mcheli.world.uavstation.UavStationGuiData.UavEntry;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Live camera-feed viewport for the selected UAV: blits the {@link UavCameraFeed} of the UAV while it is
 * loaded on the client, "NO SIGNAL" otherwise. It also keeps asking the server to stream the selected
 * UAV's terrain to this client.
 */
public class WidgetUavCameraFeed {

    private static final int HEARTBEAT_FRAMES = 20;

    private final Supplier<UavEntry> selected;
    private final UavCameraFeed feed;
    private AircraftEntity cached;
    private UUID lastSentId;
    private int heartbeat;

    public WidgetUavCameraFeed(Supplier<UavEntry> selected, UavCameraFeed feed) {
        this.selected = selected;
        this.feed = feed;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, float partialTicks) {
        g.fill(x, y, x + w, y + h, 0xFF101010);
        Minecraft mc = Minecraft.getInstance();
        UavEntry entry = this.selected.get();
        // Ask the server to stream the selected UAV's terrain to us (heartbeat; expires on close).
        sendPreviewHeartbeat(entry);
        AircraftEntity uav = resolve(mc, entry);

        if (uav == null) {
            this.feed.dropFrame();
            drawStatus(g, x, y, w, h, entry);
            return;
        }

        this.feed.request(uav);
        TextureTarget frame = this.feed.frame();
        if (frame == null) {
            drawStatus(g, x, y, w, h, entry);
            return;
        }

        RenderSystem.setShaderTexture(0, frame.getColorTextureId());
        ScreenCanvas canvas = new ScreenCanvas(g);
        canvas.resetColor();
        // Render target textures are bottom-up, so flip V.
        canvas.texturedQuad(x, y, w, h, 0.0, 1.0, 1.0, 0.0);
        g.drawString(mc.font, "● LIVE", x + 3, y + 3, 0xFFFF4040, true);
    }

    /** Stops the feed and the preview stream when the screen closes. */
    public void close() {
        this.feed.stop();
        if (this.lastSentId != null) {
            Network.sendToServer(new UavPreviewMessage(Optional.empty()));
            this.lastSentId = null;
        }
    }

    /**
     * Tells the server which UAV we are previewing so its terrain is streamed to the client. Sent
     * on selection change and as a periodic heartbeat; the server expires the request shortly after
     * the screen closes.
     */
    private void sendPreviewHeartbeat(UavEntry entry) {
        if (entry == null) return;
        this.heartbeat++;
        if (!entry.id().equals(this.lastSentId) || this.heartbeat % HEARTBEAT_FRAMES == 0) {
            Network.sendToServer(new UavPreviewMessage(Optional.of(entry.id())));
            this.lastSentId = entry.id();
        }
    }

    /** Resolves the selected UAV among client-loaded entities (cached between frames). */
    private AircraftEntity resolve(Minecraft mc, UavEntry entry) {
        if (entry == null || !entry.loaded() || mc.level == null) {
            this.cached = null;
            return null;
        }
        UUID id = entry.id();
        if (this.cached != null && !this.cached.isRemoved() && this.cached.getUUID().equals(id)) {
            return this.cached;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity ac && ac.getUUID().equals(id)) {
                this.cached = ac;
                return ac;
            }
        }
        this.cached = null;
        return null;
    }

    private void drawStatus(GuiGraphics g, int x, int y, int w, int h, UavEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        String line;
        int color;
        if (entry == null) {
            line = "NO UAV SELECTED";
            color = 0xFFAAAAAA;
        } else if (!entry.loaded()) {
            line = "NO SIGNAL";
            color = 0xFFFF5555;
        } else {
            line = "ACQUIRING…";
            color = 0xFFFFAA00;
        }
        g.drawString(mc.font, line, x + (w - mc.font.width(line)) / 2, y + h / 2 - 4, color, true);
    }
}
