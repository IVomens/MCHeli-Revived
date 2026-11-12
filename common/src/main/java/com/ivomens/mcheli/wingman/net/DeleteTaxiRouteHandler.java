package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.server.level.ServerPlayer;

/** Deletes a taxi route if the sender owns it or is an operator. */
public final class DeleteTaxiRouteHandler implements ServerMessageHandler<DeleteTaxiRouteMessage> {

    @Override
    public void handle(DeleteTaxiRouteMessage message, ServerPlayer sender) {
        AirfieldData airfield = AirfieldData.of(sender.serverLevel());
        if (!WingmanAccess.checkTaxiRouteEdit(sender, airfield.findRoute(message.routeId()))) {
            return;
        }
        airfield.deleteRoute(message.routeId());
        sender.sendSystemMessage(WingmanText.routeDeleted(message.routeId()));
    }
}
