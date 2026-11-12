package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Lets the sender jump out of the aircraft they are in. */
public final class ParachuteHandler implements ServerMessageHandler<ParachuteMessage> {

    @Override
    public void handle(ParachuteMessage message, ServerPlayer sender) {
        AircraftEntity aircraft = SeatedAircraft.of(sender);
        if (aircraft != null) {
            aircraft.crewExit().unmount(sender);
        }
    }
}
