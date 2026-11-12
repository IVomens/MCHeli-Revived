package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import com.ivomens.mcheli.vehicle.uav.UavControl;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Connects the sender to a UAV. The sender must stand next to the station and the UAV must be paired
 * with it; a UAV that cannot be located is refused.
 */
public final class UavConnectHandler implements ServerMessageHandler<UavConnectMessage> {

    @Override
    public void handle(UavConnectMessage message, ServerPlayer sender) {
        if (!(StationReach.station(sender, message.stationId()) instanceof UavStationEntity station)
                || !station.isPaired(message.uavId())) {
            return;
        }
        AircraftEntity uav = UAVTracker.locateUAV(sender.level(), message.uavId());
        // Connecting mounts the operator and links station and UAV; tracking and chunk streaming follow.
        switch (UavControl.connect(sender, station, uav)) {
            case DESTROYED -> tell(sender, "UAV is destroyed and cannot be connected to.");
            case NOT_FOUND -> tell(sender, "UAV is out of range or could not be located.");
            case OK -> sender.closeContainer();
            default -> {
            }
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
