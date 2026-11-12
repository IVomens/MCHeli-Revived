package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.livery.net.LiveryListMessage;
import com.ivomens.mcheli.livery.net.LiveryUploadBeginMessage;
import com.ivomens.mcheli.livery.net.LiveryUploadChunkMessage;
import com.ivomens.mcheli.livery.net.LiveryUploadStatusMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.client.Minecraft;

import java.util.Arrays;

/**
 * Client side of liveries (one per client, owned by the client session): tick and logout hooks, the server's replies, and the player's upload in progress.
 * The upload sends one piece per acknowledgement from the server.
 */
public final class LiveryClient {

    private static final int SWEEP_INTERVAL_TICKS = 200;

    private int ticks;
    private byte[] upload = new byte[0];
    private int uploadAcknowledged;

    public void tick() {
        LiveryKeys.handlePresses(this);
        if (++this.ticks % SWEEP_INTERVAL_TICKS == 0) {
            LiveryTextures.sweep();
        }
    }

    public void reset() {
        LiveryTextures.clear();
        this.upload = new byte[0];
        this.uploadAcknowledged = 0;
    }

    // Upload

    public boolean isUploading() {
        return this.upload.length > 0;
    }

    /** Fraction of the current upload the server has confirmed, 0 to 1. */
    public float uploadProgress() {
        return this.upload.length == 0 ? 0.0F : (float) uploadAcknowledged / this.upload.length;
    }

    void startUpload(String vehicleType, String name, byte[] png) {
        this.upload = png;
        this.uploadAcknowledged = 0;
        Network.sendToServer(new LiveryUploadBeginMessage(vehicleType, name, png.length));
    }

    void onUploadStatus(LiveryUploadStatusMessage status) {
        if (this.upload.length == 0) {
            return;
        }
        if (status.state() == LiveryManager.UploadStatus.State.RECEIVING && status.total() == this.upload.length
                && status.received() >= uploadAcknowledged && status.received() < this.upload.length) {
            this.uploadAcknowledged = status.received();
            int end = Math.min(this.upload.length, status.received() + LiveryManager.MAX_CHUNK);
            byte[] chunk = Arrays.copyOfRange(this.upload, status.received(), end);
            Network.sendToServer(new LiveryUploadChunkMessage(status.received(), chunk));
            return;
        }
        if (status.state() == LiveryManager.UploadStatus.State.RECEIVING) {
            return;
        }
        this.upload = new byte[0];
        this.uploadAcknowledged = 0;
        if (Minecraft.getInstance().screen instanceof LiveryScreen screen) {
            screen.onUploadFinished(status.state() == LiveryManager.UploadStatus.State.DONE, status.message());
        }
    }

    // Other replies

    void onList(LiveryListMessage list) {
        if (Minecraft.getInstance().screen instanceof LiveryScreen screen) {
            screen.onList(list);
        }
    }
}
