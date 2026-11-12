package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends the server settings clients have to respect, on login and whenever they change. */
public final class ServerSettingsSync {

    private ServerSettingsSync() {
    }

    public static void sendTo(ServerPlayer player) {
        Network.sendToPlayer(player, current(player.server));
    }

    /** Every player on the running server; nothing when no server runs in this process. */
    public static void sendToAll() {
        MinecraftServer server = Services.PLATFORM.getServer();
        if (server != null) {
            Network.sendToAll(current(server));
        }
    }

    private static ServerSettingsMessage current(MinecraftServer server) {
        return ServerSettingsMessage.of(ServerSettings.of(McheliConfig.get(), server.isPvpAllowed()));
    }
}
