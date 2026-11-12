package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.multiplay.MultiplayerHandler;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Destroys every aircraft; {@link MultiplayerHandler#destroyAllAircraft} checks the permission. */
public final class DestroyAllAircraftHandler implements ServerMessageHandler<DestroyAllAircraftMessage> {

    @Override
    public void handle(DestroyAllAircraftMessage message, ServerPlayer sender) {
        MultiplayerHandler.destroyAllAircraft(sender);
    }
}
