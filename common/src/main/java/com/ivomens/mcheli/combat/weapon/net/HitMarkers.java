package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Shows the shooter a hit marker when their shot hits something. */
public final class HitMarkers {

    private HitMarkers() {
    }

    /** {@code aircraft} is the vehicle the shot was fired from, or null for a shot fired on foot. */
    public static void send(@Nullable Entity aircraft, @Nullable ServerPlayer shooter) {
        if (shooter != null && !shooter.isRemoved()) {
            int aircraftId = aircraft != null && !aircraft.isRemoved() ? aircraft.getId() : -1;
            Network.sendToPlayer(shooter, new HitMarkerMessage(aircraftId));
        }
    }
}
