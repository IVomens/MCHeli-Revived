package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Reloads every weapon definition and rebuilds the weapons of every aircraft. Operators and the
 * single-player host only.
 */
public final class ReloadWeaponContentHandler implements ServerMessageHandler<ReloadWeaponContentMessage> {

    @Override
    public void handle(ReloadWeaponContentMessage message, ServerPlayer sender) {
        if (!ContentReloads.allowed(sender)) {
            return;
        }
        Weapons.registry().reloadAll();
        for (AircraftEntity aircraft : ContentReloads.allAircraft(sender)) {
            aircraft.changeType(aircraft.spec().name());
            aircraft.seats().create(UUID.randomUUID().toString());
        }
    }
}
