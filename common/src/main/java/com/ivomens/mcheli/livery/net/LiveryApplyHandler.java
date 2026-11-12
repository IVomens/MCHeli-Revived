package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Puts a livery on an aircraft. {@link LiveryManager#apply} checks that the sender may change the
 * aircraft's livery and that the livery fits the aircraft.
 */
public final class LiveryApplyHandler implements ServerMessageHandler<LiveryApplyMessage> {

    @Override
    public void handle(LiveryApplyMessage message, ServerPlayer sender) {
        LiveryManager manager = LiveryManager.of(sender.server);
        Entity target = ServerMessageHandler.entityNear(sender, message.aircraftId());
        if (manager == null || !(target instanceof AircraftEntity aircraft)) {
            return;
        }
        LiveryManager.Result result = manager.apply(sender, aircraft, message.liveryId());
        LiveryListReply.send(manager, sender, aircraft.identity().typeName(), result.message(), !result.success());
    }
}
