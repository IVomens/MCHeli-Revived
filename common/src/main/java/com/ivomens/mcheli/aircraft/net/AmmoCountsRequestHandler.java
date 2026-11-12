package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.net.AmmoCountsRequestMessage;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Answers with the ammunition of every weapon of an aircraft the sender can see. */
public final class AmmoCountsRequestHandler implements ServerMessageHandler<AmmoCountsRequestMessage> {

    @Override
    public void handle(AmmoCountsRequestMessage message, ServerPlayer sender) {
        if (ServerMessageHandler.entityNear(sender, message.aircraftId()) instanceof AircraftEntity aircraft) {
            AmmoCounts.sendAll(aircraft, sender);
        }
    }
}
