package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.SeatEntity;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/** The aircraft a player sits in, for the seat messages. */
final class SeatedAircraft {

    private SeatedAircraft() {
    }

    /** The aircraft of the player's seat, or the aircraft they ride or control; null for none. */
    @Nullable
    static AircraftEntity of(ServerPlayer player) {
        return player.getVehicle() instanceof SeatEntity seat
                ? seat.getParent()
                : RiddenVehicle.of(player);
    }
}
