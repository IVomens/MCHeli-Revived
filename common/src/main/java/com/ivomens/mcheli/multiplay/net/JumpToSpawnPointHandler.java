package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.multiplay.Multiplay;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Teleports the sender to their spawn point. */
public final class JumpToSpawnPointHandler implements ServerMessageHandler<JumpToSpawnPointMessage> {

    @Override
    public void handle(JumpToSpawnPointMessage message, ServerPlayer sender) {
        Multiplay.jumpSpawnPoint(sender);
    }
}
