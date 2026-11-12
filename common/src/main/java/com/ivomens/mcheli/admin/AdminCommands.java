package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.config.AdminAccess;
import com.ivomens.mcheli.config.McheliConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * The server owner tools under {@code /mcheli}: {@code lock} and {@code unlock} for every player, and
 * {@code perf} and {@code vehicles} for operators (the level of {@code AdminCommandPermission}, the
 * {@code mcheli.admin} permission node on NeoForge, or a {@code CommandPermission} line naming the player for
 * that sub-command). Like the rest of {@code /mcheli}, they are gone while {@code EnableCommand} is false.
 * See docs/SERVER_ADMIN.md.
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcheli")
                .then(LockCommand.lock())
                .then(LockCommand.unlock())
                .then(PerfCommand.perf().requires(operator("perf")))
                .then(VehicleCommands.vehicles().requires(operator("vehicles"))));
    }

    private static Predicate<CommandSourceStack> operator(String command) {
        return source -> AdminAccess.isAdmin(source) || source.getEntity() instanceof ServerPlayer player
                && McheliConfig.commands().allows(command, player.getGameProfile().getName());
    }
}
