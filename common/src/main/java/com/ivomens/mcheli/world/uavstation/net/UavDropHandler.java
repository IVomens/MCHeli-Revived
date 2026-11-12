package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Drops a UAV from a station the sender stands next to, and frees the UAV for other stations. */
public final class UavDropHandler implements ServerMessageHandler<UavDropMessage> {

    @Override
    public void handle(UavDropMessage message, ServerPlayer sender) {
        if (!(StationReach.station(sender, message.stationId()) instanceof UavStationEntity station)
                || !station.unpairUav(message.uavId())) {
            return;
        }
        // Clear the UAV's owning station so it can be paired again.
        AircraftEntity uav = UAVTracker.locateUAV(sender.level(), message.uavId());
        if (uav != null) {
            uav.uavLink().setPairedStation(null);
        }
        sender.sendSystemMessage(Component.literal("Dropped UAV from station."));
        sender.closeContainer();
    }
}
