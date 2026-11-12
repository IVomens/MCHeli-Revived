package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Answers with the seat entities of an aircraft the sender can see. */
public final class SeatListRequestHandler implements ServerMessageHandler<SeatListRequestMessage> {

    @Override
    public void handle(SeatListRequestMessage message, ServerPlayer sender) {
        if (!(ServerMessageHandler.entityNear(sender, message.aircraftId()) instanceof AircraftEntity aircraft)) {
            return;
        }
        SeatEntity[] seats = aircraft.seats().all();
        List<Integer> seatIds = new ArrayList<>(seats.length);
        for (int i = 0; i < seats.length; i++) {
            SeatEntity seat = aircraft.seats().seat(i);
            seatIds.add(seat != null ? seat.getId() : -1);
        }
        Network.sendToPlayer(sender, new SeatListMessage(aircraft.getId(), seatIds));
    }
}
