package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.net.AirburstDistanceMessage;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Sets the airburst distance of the sender's current weapon, if the sender is aboard that aircraft. */
public final class AirburstDistanceHandler implements ServerMessageHandler<AirburstDistanceMessage> {

    @Override
    public void handle(AirburstDistanceMessage message, ServerPlayer sender) {
        if (ServerMessageHandler.entityNear(sender, message.aircraftId()) instanceof AircraftEntity aircraft
                && aircraft.seats().isAboard(sender)) {
            WeaponGroup weaponSet = aircraft.weapons().current(sender);
            if (weaponSet != null && weaponSet.currentMount() != null) {
                weaponSet.currentMount().setAirburstDistance(Math.max(0, message.distance()));
            }
        }
    }
}
