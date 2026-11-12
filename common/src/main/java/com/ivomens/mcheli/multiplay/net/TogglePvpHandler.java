package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Switches PvP for the whole server and tells every client. */
public final class TogglePvpHandler implements ServerMessageHandler<TogglePvpMessage> {

    private static final int OPERATOR_LEVEL = 2;

    @Override
    public void handle(TogglePvpMessage message, ServerPlayer sender) {
        if (sender.hasPermissions(OPERATOR_LEVEL)) {
            sender.server.setPvpAllowed(!sender.server.isPvpAllowed());
            ServerSettingsSync.sendToAll();
        }
    }
}
