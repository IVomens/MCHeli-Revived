package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Stores the command string of the aircraft the sender rides or controls. */
public final class AircraftCommandHandler implements ServerMessageHandler<AircraftCommandMessage> {

    @Override
    public void handle(AircraftCommandMessage message, ServerPlayer sender) {
        AircraftEntity aircraft = RiddenVehicle.of(sender);
        if (aircraft != null) {
            aircraft.wreck().setCommand(message.command(), sender);
        }
    }
}
