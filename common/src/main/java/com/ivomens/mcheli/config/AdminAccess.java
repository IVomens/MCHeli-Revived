package com.ivomens.mcheli.config;

import com.ivomens.mcheli.platform.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who counts as an operator for the server owner tools: whoever holds the permission node
 * {@code mcheli.admin} (NeoForge, with a permission mod) or has the operator level
 * {@code AdminCommandPermission} names.
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /** The operator level of {@code AdminCommandPermission}. */
    public static int level() {
        return McheliConfig.admin().commandPermission();
    }

    /** Server side. On a client this only knows the player's operator level. */
    public static boolean isAdmin(Player player) {
        return Permissions.isAdmin(player, level());
    }

    /** A player runs the command as that player; the console and command blocks by their own level. */
    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? isAdmin(player) : source.hasPermission(level());
    }
}
