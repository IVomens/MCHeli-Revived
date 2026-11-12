package com.ivomens.mcheli.multiplay;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a server's operators asked its players for: screenshots (and the one upload in progress) and mod
 * lists. Every server has its own, so nothing carries over to the next world.
 */
public final class OperatorRequests {

    private static final int MAX_REQUEST_ID = 1000000;
    /** How long a requested player has to start sending their screenshot. */
    private static final long SCREENSHOT_REQUEST_TIMEOUT_MS = 60_000L;

    /** Players an operator asked for a screenshot, with the time the request runs out. */
    private final Map<UUID, Long> screenshotRequests = new ConcurrentHashMap<>();
    /** The operator waiting for a mod list, matched by {@link #modListRequestId}. */
    @Nullable
    private Player modListRequester;
    private int modListRequestId;
    /** Screenshot upload in progress, the player sending it and the last progress logged. */
    @Nullable
    private byte[] upload;
    private String uploader = "";
    private double loggedPercent;

    /** Implemented on {@link MinecraftServer} by a mixin. */
    public interface Holder {
        OperatorRequests mcheli$operatorRequests();
    }

    public static OperatorRequests of(MinecraftServer server) {
        return ((Holder) server).mcheli$operatorRequests();
    }

    /** Lets the player upload one screenshot within the next minute. */
    public void requestScreenshot(ServerPlayer player) {
        this.screenshotRequests.put(player.getUUID(), System.currentTimeMillis() + SCREENSHOT_REQUEST_TIMEOUT_MS);
    }

    /**
     * Uses up the player's pending screenshot request. Uploads nobody asked for are refused, so a
     * client cannot make the server store arbitrary files.
     */
    public boolean takeScreenshotRequest(ServerPlayer player) {
        Long expires = this.screenshotRequests.remove(player.getUUID());
        return expires != null && expires >= System.currentTimeMillis();
    }

    /** Remembers {@code requester} as the one waiting for a mod list and returns the new request id. */
    public int requestModList(@Nullable Player requester) {
        this.modListRequester = requester;
        this.modListRequestId = this.modListRequestId >= MAX_REQUEST_ID ? 1 : this.modListRequestId + 1;
        return this.modListRequestId;
    }

    /** Whether a mod list sent for {@code requestId} answers the latest request. */
    public boolean answersModListRequest(int requestId) {
        return requestId != 0 && requestId == this.modListRequestId;
    }

    /** The operator waiting for the mod lists, or null when the console asked. */
    @Nullable
    public Player modListRequester() {
        return this.modListRequester;
    }

    /** Forgets a player who leaves. */
    public void forget(Player player) {
        if (this.modListRequester == player) {
            this.modListRequester = null;
        }
    }

    /** Starts collecting a screenshot of {@code total} bytes; an upload still in progress is dropped. */
    public void startUpload(String player, int total) {
        if (this.upload != null && !this.uploader.isEmpty()) {
            MultiplayerHandler.logError("[mcheli]Err1: Saving the %s screenshot to server FAILED!!!", this.uploader);
        }
        this.upload = new byte[total];
        this.uploader = player;
        this.loggedPercent = 0.0;
    }

    /** The screenshot being collected, or null. */
    @Nullable
    public byte[] upload() {
        return this.upload;
    }

    public String uploader() {
        return this.uploader;
    }

    public double loggedPercent() {
        return this.loggedPercent;
    }

    public void setLoggedPercent(double percent) {
        this.loggedPercent = percent;
    }

    public void resetUpload() {
        this.upload = null;
        this.uploader = "";
        this.loggedPercent = 0.0;
    }
}
