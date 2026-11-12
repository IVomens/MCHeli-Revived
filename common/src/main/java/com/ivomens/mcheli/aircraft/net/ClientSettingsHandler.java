package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.vehicle.settings.PilotSettings;
import net.minecraft.server.level.ServerPlayer;

/** Applies the sender's client settings: the pilot's to the aircraft itself, the shader flag to every seat. */
public final class ClientSettingsHandler implements ServerMessageHandler<ClientSettingsMessage> {

    @Override
    public void handle(ClientSettingsMessage message, ServerPlayer sender) {
        AircraftEntity aircraft = RiddenVehicle.of(sender);
        if (aircraft == null) {
            return;
        }
        int seatId = aircraft.seats().seatIdOf(sender);
        // Only the pilot decides how the aircraft itself behaves.
        if (seatId == 0) {
            aircraft.pilotPreferences().setPilotSettings(new PilotSettings(message.dismountAll(),
                    message.heliAutoThrottleDown(), message.planeAutoThrottleDown(), message.tankAutoThrottleDown()));
        }
        if (seatId >= 0) {
            aircraft.cameras().camera().setShaderSupport(seatId, message.shaderSupport());
        }
    }
}
