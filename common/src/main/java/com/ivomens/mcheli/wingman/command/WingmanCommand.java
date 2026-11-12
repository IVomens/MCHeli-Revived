package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /wingman}: formation control (follow, stop, status, dist, maxwings), attack control (engage,
 * auto, hold, weapon, minalt, maxalt, alt), UAV spawning, markers, node-list routes and missions,
 * mission orders and the mission planner screen. Each group of subcommands has its own class; this
 * one assembles the tree and holds what they share.
 *
 * <p>The command is open to everyone: players can only order their own wingmen, and subcommands that
 * change the global config or spawn aircraft check for an operator themselves. The words and their
 * order are the ones the old single-string command took, so saved command lines keep working.
 */
public final class WingmanCommand {

    static final String USAGE = "/wingman <follow|stop|status|dist|maxwings|engage|auto|hold|weapon"
            + "|minalt|maxalt|alt|spawnuav|marker|route|mission|order|gui> [args]";

    private WingmanCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wingman")
                .executes(ctx -> usage(ctx, USAGE));
        FormationCommands.addTo(root);
        AttackCommands.addTo(root);
        SetupCommands.addTo(root);
        MissionCommands.addTo(root);
        OrderCommands.addTo(root);
        dispatcher.register(root);
    }

    /** What a subcommand does once it has the player who ran it. */
    @FunctionalInterface
    interface PlayerAction {
        void run(ServerPlayer player, WingmanService wingmen) throws CommandSyntaxException;
    }

    /** Runs the action for a player; anyone else (the console, a command block) is turned away. */
    static int asPlayer(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(WingmanText.line(ChatFormatting.RED, "command.player_only"));
            return Command.SINGLE_SUCCESS;
        }
        action.run(player, WingmanService.of(player));
        return Command.SINGLE_SUCCESS;
    }

    /** Shows a usage line, to players only, as the command always has. */
    static int usage(CommandContext<CommandSourceStack> ctx, String usage) throws CommandSyntaxException {
        return asPlayer(ctx, (player, wingmen) -> send(player, WingmanText.usage(usage)));
    }

    static void send(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
