package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import com.ivomens.mcheli.world.uavstation.net.UavPreviewMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Starts or stops streaming a UAV's terrain to the sender. Only a UAV paired to the station the sender
 * occupies is streamed, so a client cannot force arbitrary chunks to load.
 */
public final class UavPreviewHandler implements ServerMessageHandler<UavPreviewMessage> {

    @Override
    public void handle(UavPreviewMessage message, ServerPlayer sender) {
        UUID uavId = message.uavId().orElse(null);
        if (uavId != null && sender.getVehicle() instanceof UavStationEntity station && station.isPaired(uavId)) {
            WingmanService.of(sender).uavPreviews().setPreview(sender.getUUID(), uavId, sender.level().getGameTime());
        } else {
            WingmanService.of(sender).uavPreviews().clearPreview(sender.getUUID());
        }
    }
}
