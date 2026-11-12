package com.ivomens.mcheli.bench;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.networking.Network;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mcheli benchmark client [quick]}: starts the in-game client benchmark (see
 * docs/PLAYTEST.md) on the player's own game. Only in a single player world (the integrated server,
 * nobody else connected), only for a player allowed to cheat, and only while {@code ClientBenchmark}
 * in mcheli.cfg is true (the default). It spawns and removes vehicles around the player, so it is never
 * offered on a dedicated server.
 */
public final class BenchmarkCommand {

    private static final int OPERATOR = 2;

    private BenchmarkCommand() {
    }

    /** Adds {@code benchmark} to the {@code /mcheli} command. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcheli").then(Commands.literal("benchmark")
                .requires(source -> source.hasPermission(OPERATOR) && !source.getServer().isDedicatedServer())
                .then(Commands.literal("client")
                        .executes(context -> start(context.getSource(), false))
                        .then(Commands.literal("quick").executes(context -> start(context.getSource(), true))))));
    }

    /** Declares the message that tells the client to start. */
    public static void registerMessages(MessageRegistry registry) {
        registry.clientbound(StartClientBenchmarkMessage.TYPE);
    }

    private static int start(CommandSourceStack source, boolean quick) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (!McheliConfig.commands().clientBenchmark()) {
            source.sendFailure(Component.translatableWithFallback("commands.mcheli.benchmark.disabled",
                    "The client benchmark is switched off (ClientBenchmark = false in mcheli.cfg)."));
            return 0;
        }
        if (player == null || server.isDedicatedServer() || !server.isSingleplayerOwner(player.getGameProfile())
                || server.getPlayerCount() != 1) {
            source.sendFailure(Component.translatableWithFallback("commands.mcheli.benchmark.single_player",
                    "The client benchmark only runs in a single player world with nobody else connected."));
            return 0;
        }
        if (player.getVehicle() != null) {
            source.sendFailure(Component.translatableWithFallback("commands.mcheli.benchmark.riding",
                    "Get out of the vehicle first."));
            return 0;
        }
        Network.sendToPlayer(player, new StartClientBenchmarkMessage(quick));
        return 1;
    }
}
