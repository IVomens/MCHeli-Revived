package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Answers with the selected weapon of every seat of an aircraft the sender can see. */
public final class WeaponStatusRequestHandler implements ServerMessageHandler<WeaponStatusRequestMessage> {

    @Override
    public void handle(WeaponStatusRequestMessage message, ServerPlayer sender) {
        if (!(ServerMessageHandler.entityNear(sender, message.aircraftId()) instanceof AircraftEntity aircraft)) {
            return;
        }
        int seatCount = aircraft.seats().count() + 1;
        List<Byte> weaponIds = new ArrayList<>(seatCount);
        for (int seatId = 0; seatId < seatCount; seatId++) {
            weaponIds.add((byte) aircraft.weapons().idForSeat(seatId));
        }
        Network.sendToPlayer(sender, new WeaponStatusMessage(aircraft.getId(), weaponIds));
    }
}
