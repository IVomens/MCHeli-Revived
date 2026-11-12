package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** Runs commands configured on an aircraft (e.g. "on destroyed") with full permission and no chat feedback. */
public final class DummyCommandSender {

    private static final int OP_PERMISSION_LEVEL = 4;

    private DummyCommandSender() {
    }

    public static void execCommand(String command) {
        MinecraftServer server = Services.PLATFORM.getServer();
        if (server == null || command == null || command.isBlank()) {
            return;
        }
        String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
        CommandSourceStack source = server.createCommandSourceStack().withPermission(OP_PERMISSION_LEVEL).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, withoutSlash);
    }
}
