package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/** The UAV station a station screen message names, if the sender can reach it. */
final class StationReach {

    /** Operators must stand this close to the station. */
    private static final double MAX_DISTANCE = 16.0;

    private StationReach() {
    }

    @Nullable
    static UavStationEntity station(ServerPlayer player, int stationId) {
        if (ServerMessageHandler.entityNear(player, stationId) instanceof UavStationEntity station
                && player.distanceToSqr(station) <= MAX_DISTANCE * MAX_DISTANCE) {
            return station;
        }
        return null;
    }
}
