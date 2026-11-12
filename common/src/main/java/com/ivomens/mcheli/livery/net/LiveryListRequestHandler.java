package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Answers the livery screen with the list for one vehicle type. */
public final class LiveryListRequestHandler implements ServerMessageHandler<LiveryListRequestMessage> {

    @Override
    public void handle(LiveryListRequestMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        if (manager != null) {
            LiveryListReply.send(manager, sender, message.vehicleType(), "", false);
        }
    }
}
