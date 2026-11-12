package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.multiplay.Multiplay;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Shuffles the players into teams. */
public final class ShuffleTeamsHandler implements ServerMessageHandler<ShuffleTeamsMessage> {

    @Override
    public void handle(ShuffleTeamsMessage message, ServerPlayer sender) {
        Multiplay.shuffleTeam(sender);
    }
}
