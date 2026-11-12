package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Reloads the definition of the sender's aircraft type and rebuilds every aircraft of that type.
 * Reloading touches every loaded aircraft: operators and the single-player host only.
 */
public final class ReloadVehicleContentHandler implements ServerMessageHandler<ReloadVehicleContentMessage> {

    @Override
    public void handle(ReloadVehicleContentMessage message, ServerPlayer sender) {
        if (!ContentReloads.allowed(sender)) {
            return;
        }
        AircraftEntity ridden = RiddenVehicle.of(sender);
        if (ridden == null || ridden.spec() == null) {
            return;
        }
        String name = ridden.spec().name();
        for (AircraftEntity aircraft : ContentReloads.allAircraft(sender)) {
            if (aircraft.spec().name().equals(name)) {
                aircraft.changeType(name);
                aircraft.seats().create(UUID.randomUUID().toString());
                aircraft.body().onDefinitionReloaded();
            }
        }
    }
}
