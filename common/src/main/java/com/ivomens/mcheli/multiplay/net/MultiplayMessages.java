package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/**
 * Multiplayer messages: the scoreboard screen's actions, operator requests for screenshots and mod
 * lists, spotting with the range finder, titles and the server settings clients have to respect.
 */
public final class MultiplayMessages {

    private MultiplayMessages() {
    }

    public static void register(MessageRegistry registry) {
        // Scoreboard screen
        registry.serverbound(ShuffleTeamsMessage.TYPE, new ShuffleTeamsHandler());
        registry.serverbound(JumpToSpawnPointMessage.TYPE, new JumpToSpawnPointHandler());
        registry.serverbound(TogglePvpMessage.TYPE, new TogglePvpHandler());
        registry.serverbound(DestroyAllAircraftMessage.TYPE, new DestroyAllAircraftHandler());
        registry.serverbound(RunCommandMessage.TYPE, new RunCommandHandler());

        // Operator requests
        registry.clientbound(ScreenshotRequestMessage.TYPE);
        registry.serverbound(ScreenshotChunkMessage.TYPE, new ScreenshotChunkHandler());
        registry.clientbound(ModListRequestMessage.TYPE);
        registry.serverbound(ModListMessage.TYPE, new ModListHandler());
        registry.clientbound(ModListRelayMessage.TYPE);

        // Spotting and marking
        registry.clientbound(SpotEnemyMessage.TYPE);
        registry.clientbound(MarkPositionMessage.TYPE);

        registry.clientbound(TitleMessage.TYPE);
        registry.clientbound(ServerSettingsMessage.TYPE);
    }
}
