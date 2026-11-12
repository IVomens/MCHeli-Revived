package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.multiplay.MultiplayerHandler;
import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Collects a screenshot an operator asked for. The first piece must answer an open request, and only
 * the player whose upload is in progress may add to it.
 */
public final class ScreenshotChunkHandler implements ServerMessageHandler<ScreenshotChunkMessage> {

    /** Upload progress is logged in steps of this many percent. */
    private static final double PROGRESS_LOG_STEP = 10.0;

    @Override
    public void handle(ScreenshotChunkMessage message, ServerPlayer sender) {
        int offset = message.offset();
        int total = message.totalSize();
        if (offset < 0 || total <= 0 || total > ScreenshotChunkMessage.MAX_TOTAL) {
            return;
        }
        String playerName = sender.getGameProfile().getName();
        OperatorRequests requests = OperatorRequests.of(sender.server);
        try {
            if (offset == 0) {
                if (!requests.takeScreenshotRequest(sender)) {
                    ModLogger.warn("Refused a screenshot from {} that nobody requested", playerName);
                    return;
                }
                requests.startUpload(playerName, total);
            }
            if (!appendChunk(requests, playerName, message)) {
                return;
            }
            int received = offset + message.data().length;
            logProgress(requests, playerName, received, total);
            if (received >= total) {
                MultiplayerHandler.saveScreenshot(playerName.replaceAll("[^A-Za-z0-9_\\-]", "_"),
                        requests.upload());
                requests.resetUpload();
            }
        } catch (Exception e) {
            ModLogger.error("Could not save the screenshot of " + playerName, e);
            requests.resetUpload();
        }
    }

    /** Copies the piece into the upload buffer. Only the player whose upload is in progress may append. */
    private static boolean appendChunk(OperatorRequests requests, String playerName, ScreenshotChunkMessage message) {
        byte[] image = requests.upload();
        byte[] data = message.data();
        boolean ownUpload = image != null && playerName.equals(requests.uploader());
        if (!ownUpload || image.length != message.totalSize() || (long) message.offset() + data.length > image.length) {
            if (ownUpload) {
                requests.resetUpload();
            }
            return false;
        }
        System.arraycopy(data, 0, image, message.offset(), data.length);
        return true;
    }

    private static void logProgress(OperatorRequests requests, String playerName, int received, int total) {
        double percent = (double) received / total * 100.0;
        double lastPercent = requests.loggedPercent();
        if (percent - lastPercent >= PROGRESS_LOG_STEP || lastPercent == 0.0) {
            MultiplayerHandler.logInfo("[mcheli]Saving %s screenshot to server. %.0f%% : %d / %d bytes",
                    playerName, percent, received, total);
            requests.setLoggedPercent(percent);
        }
    }
}
