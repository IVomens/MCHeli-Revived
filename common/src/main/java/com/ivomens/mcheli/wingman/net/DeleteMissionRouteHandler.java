package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.server.level.ServerPlayer;

/** Deletes a mission route if the sender created it or is an operator. */
public final class DeleteMissionRouteHandler implements ServerMessageHandler<DeleteMissionRouteMessage> {

    @Override
    public void handle(DeleteMissionRouteMessage message, ServerPlayer sender) {
        String name = message.name();
        WingmanService wingmen = WingmanService.of(sender);
        if (!wingmen.access().checkRouteEdit(sender, name)) {
            return;
        }
        sender.sendSystemMessage(wingmen.missionRoutes().remove(name)
                ? WingmanText.routeDeleted(name) : WingmanText.routeNotFound(name));
    }
}
