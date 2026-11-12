package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Refills a weapon of the aircraft the sender rides or controls, and of no other. */
public final class ResupplyHandler implements ServerMessageHandler<ResupplyMessage> {

    @Override
    public void handle(ResupplyMessage message, ServerPlayer sender) {
        if (ServerMessageHandler.entityNear(sender, message.aircraftId()) instanceof AircraftEntity aircraft
                && RiddenVehicle.of(sender) == aircraft
                && message.weaponId() >= 0 && message.weaponId() < aircraft.weapons().count()) {
            aircraft.ammoSupply().supply(aircraft.weapons().get(message.weaponId()));
        }
    }
}
