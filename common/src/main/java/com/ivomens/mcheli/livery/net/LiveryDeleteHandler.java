package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Deletes a livery; {@link LiveryManager#delete} allows it for the owner and operators only. */
public final class LiveryDeleteHandler implements ServerMessageHandler<LiveryDeleteMessage> {

    @Override
    public void handle(LiveryDeleteMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        if (manager != null) {
            LiveryManager.Result result = manager.delete(sender, message.liveryId());
            LiveryListReply.send(manager, sender, message.vehicleType(), result.message(), !result.success());
        }
    }
}
