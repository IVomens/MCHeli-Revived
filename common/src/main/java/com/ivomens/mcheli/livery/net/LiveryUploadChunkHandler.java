package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/**
 * Takes the next piece of an upload and acknowledges it. Once the upload is complete the player also
 * gets the refreshed list.
 */
public final class LiveryUploadChunkHandler implements ServerMessageHandler<LiveryUploadChunkMessage> {

    @Override
    public void handle(LiveryUploadChunkMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        if (manager == null) {
            return;
        }
        LiveryManager.UploadStatus status = manager.receiveChunk(sender, message.offset(), message.data());
        Network.sendToPlayer(sender, LiveryUploadStatusMessage.of(status));
        if (status.livery() != null) {
            LiveryListReply.send(manager, sender, status.livery().vehicleType(), "", false);
        }
    }
}
