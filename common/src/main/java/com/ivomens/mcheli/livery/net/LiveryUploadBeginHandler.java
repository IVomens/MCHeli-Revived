package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Starts an upload; {@link LiveryManager#beginUpload} enforces the size and per-player limits. */
public final class LiveryUploadBeginHandler implements ServerMessageHandler<LiveryUploadBeginMessage> {

    @Override
    public void handle(LiveryUploadBeginMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        if (manager != null) {
            LiveryManager.UploadStatus status = manager.beginUpload(sender, message.vehicleType(), message.name(),
                    message.totalBytes());
            Network.sendToPlayer(sender, LiveryUploadStatusMessage.of(status));
        }
    }
}
