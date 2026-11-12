package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Moves the sender to another seat of the aircraft they are in. */
public final class SwitchSeatHandler implements ServerMessageHandler<SwitchSeatMessage> {

    @Override
    public void handle(SwitchSeatMessage message, ServerPlayer sender) {
        AircraftEntity aircraft = SeatedAircraft.of(sender);
        if (aircraft == null) {
            return;
        }
        if (message.next()) {
            aircraft.seats().switchNext(sender);
        } else {
            aircraft.seats().switchPrevious(sender);
        }
    }
}
