package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.server.level.ServerPlayer;

/** Sets the UAV spawn offset of the station the sender is in. */
public final class UavSpawnOffsetHandler implements ServerMessageHandler<UavSpawnOffsetMessage> {

    @Override
    public void handle(UavSpawnOffsetMessage message, ServerPlayer sender) {
        if (sender.getVehicle() instanceof UavStationEntity station) {
            station.setUavPosition(message.x(), message.y(), message.z());
        }
    }
}
