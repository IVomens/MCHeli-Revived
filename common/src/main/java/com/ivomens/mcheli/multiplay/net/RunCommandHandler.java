package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Runs a command with the sender's own permissions, exactly like typing it in chat. */
public final class RunCommandHandler implements ServerMessageHandler<RunCommandMessage> {

    @Override
    public void handle(RunCommandMessage message, ServerPlayer sender) {
        if (!message.command().isEmpty()) {
            sender.server.getCommands().performPrefixedCommand(sender.createCommandSourceStack(), message.command());
        }
    }
}
