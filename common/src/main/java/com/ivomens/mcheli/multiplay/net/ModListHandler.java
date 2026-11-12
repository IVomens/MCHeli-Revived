package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.multiplay.MultiplayerHandler;
import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.stream.Stream;

/**
 * Passes a player's mod list on to the operator who asked for it, or logs it when the request came
 * from the console. Answers to an older request are ignored.
 */
public final class ModListHandler implements ServerMessageHandler<ModListMessage> {

    @Override
    public void handle(ModListMessage message, ServerPlayer sender) {
        OperatorRequests requests = OperatorRequests.of(sender.server);
        if (!requests.answersModListRequest(message.requestId())) {
            return;
        }
        String header = "###### " + sender.getGameProfile().getName() + " ######";
        if (requests.modListRequester() instanceof ServerPlayer requester) {
            List<String> lines = message.lines();
            // The relayed copy has to stay within the line limit the requester's client reads.
            if (message.firstChunk() && lines.size() < ModListMessage.MAX_LINES) {
                lines = Stream.concat(Stream.of(ChatFormatting.RED + header), lines.stream()).toList();
            }
            Network.sendToPlayer(requester, new ModListRelayMessage(lines));
            return;
        }
        if (message.firstChunk()) {
            MultiplayerHandler.logInfo(header);
        }
        message.lines().forEach(MultiplayerHandler::logInfo);
    }
}
