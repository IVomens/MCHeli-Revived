package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import net.minecraft.server.level.ServerPlayer;

/** Releases a wingman the sender may order. */
public final class WingmanStopHandler implements ServerMessageHandler<WingmanStopMessage> {

    @Override
    public void handle(WingmanStopMessage message, ServerPlayer sender) {
        WingmanService wingmen = WingmanService.of(sender);
        if (wingmen.access().existing(sender, message.wingmanId()) != null) {
            wingmen.roster().remove(message.wingmanId());
        }
    }
}
