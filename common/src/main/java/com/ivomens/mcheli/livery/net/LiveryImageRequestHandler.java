package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Queues a livery image for download; the manager sends it in pieces over the next ticks. */
public final class LiveryImageRequestHandler implements ServerMessageHandler<LiveryImageRequestMessage> {

    @Override
    public void handle(LiveryImageRequestMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        if (manager != null) {
            manager.requestImage(sender, message.liveryId());
        }
    }
}
