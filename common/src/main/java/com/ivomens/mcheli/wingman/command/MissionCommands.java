package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.brain.NodeMission;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ivomens.mcheli.wingman.command.WingmanCommand.asPlayer;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.send;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.usage;
import static com.ivomens.mcheli.wingman.text.WingmanText.line;

/**
 * Node-list routes ({@code route create <name> <node...>|list|delete <name>|show <name>}) and the
 * missions that fly them ({@code mission assign <aircraft> <route>|abort [aircraft]|status}).
 */
final class MissionCommands {

    private static final String ROUTE_USAGE =
            "/wingman route <create <name> <node...>|list|delete <name>|show <name>>";

    private MissionCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("route")
                        .executes(ctx -> usage(ctx, ROUTE_USAGE))
                        .then(Commands.literal("create")
                                .executes(MissionCommands::createUsage)
                                .then(routeName()
                                        .executes(MissionCommands::createUsage)
                                        .then(Commands.argument("nodes", StringArgumentType.greedyString())
                                                .executes(ctx -> asPlayer(ctx, (player, wingmen) -> createRoute(player,
                                                        wingmen, StringArgumentType.getString(ctx, "route"),
                                                        StringArgumentType.getString(ctx, "nodes")))))))
                        .then(Commands.literal("list").executes(ctx -> asPlayer(ctx, MissionCommands::listRoutes)))
                        .then(Commands.literal("delete")
                                .executes(ctx -> usage(ctx, "/wingman route delete <name>"))
                                .then(routeName().executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        deleteRoute(player, wingmen, StringArgumentType.getString(ctx, "route"))))))
                        .then(Commands.literal("show")
                                .executes(ctx -> usage(ctx, "/wingman route show <name>"))
                                .then(routeName().executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        showRoute(player, wingmen, StringArgumentType.getString(ctx, "route")))))))
                .then(Commands.literal("mission")
                        .executes(ctx -> usage(ctx, "/wingman mission <assign <uuid> <route>|abort [uuid]|status>"))
                        .then(Commands.literal("assign")
                                .executes(ctx -> usage(ctx, "/wingman mission assign <uuid> <route>"))
                                .then(Commands.argument("aircraft", UuidArgument.uuid())
                                        .then(routeName().executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                                assign(player, wingmen, UuidArgument.getUuid(ctx, "aircraft"),
                                                        StringArgumentType.getString(ctx, "route")))))))
                        .then(Commands.literal("abort")
                                .executes(ctx -> asPlayer(ctx, MissionCommands::abortAll))
                                .then(Commands.argument("aircraft", UuidArgument.uuid())
                                        .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                                abort(player, wingmen, UuidArgument.getUuid(ctx, "aircraft"))))))
                        .then(Commands.literal("status").executes(ctx -> asPlayer(ctx, MissionCommands::status))));
    }

    /** A route name, suggested from the saved routes. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> routeName() {
        return Commands.argument("route", StringArgumentType.string())
                .suggests((ctx, builder) -> ctx.getSource().getEntity() instanceof ServerPlayer player
                        ? SharedSuggestionProvider.suggest(WingmanService.of(player).missionRoutes().names(), builder)
                        : builder.buildFuture());
    }

    private static int createUsage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return asPlayer(ctx, (player, wingmen) -> send(player, line(ChatFormatting.GRAY, "route.create_usage")));
    }

    private static void createRoute(ServerPlayer player, WingmanService wingmen, String name, String nodeText) {
        if (!wingmen.access().checkRouteEdit(player, name)) {
            return;
        }
        List<MissionNode> nodes = new ArrayList<>();
        for (String token : nodeText.trim().split("\\s+")) {
            try {
                nodes.add(MissionNode.parse(token));
            } catch (RuntimeException e) {
                send(player, WingmanText.invalidNode(token));
                return;
            }
        }
        wingmen.missionRoutes().put(name, nodes, player.getUUID());
        send(player, WingmanText.routeSaved(name, nodes.size()));
    }

    private static void listRoutes(ServerPlayer player, WingmanService wingmen) {
        Set<String> names = wingmen.missionRoutes().names();
        if (names.isEmpty()) {
            send(player, line(ChatFormatting.GRAY, "route.none"));
            return;
        }
        send(player, line(ChatFormatting.YELLOW, "route.header"));
        names.forEach(name -> send(player, line(ChatFormatting.GRAY, "route.entry", name)));
    }

    private static void deleteRoute(ServerPlayer player, WingmanService wingmen, String name) {
        if (!wingmen.access().checkRouteEdit(player, name)) {
            return;
        }
        send(player, wingmen.missionRoutes().remove(name) ? WingmanText.routeDeleted(name)
                : WingmanText.routeNotFound(name));
    }

    private static void showRoute(ServerPlayer player, WingmanService wingmen, String name) {
        List<MissionNode> nodes = wingmen.missionRoutes().get(name);
        if (nodes == null) {
            send(player, WingmanText.routeNotFound(name));
            return;
        }
        send(player, line(ChatFormatting.YELLOW, "route.show_header", name));
        for (int i = 0; i < nodes.size(); i++) {
            send(player, line(ChatFormatting.GRAY, "route.node", i, nodes.get(i).toString()));
        }
    }

    private static void assign(ServerPlayer player, WingmanService wingmen, UUID aircraftId, String routeName) {
        Entity aircraft = player.serverLevel().getEntity(aircraftId);
        if (aircraft == null) {
            send(player, WingmanText.entityNotFound());
            return;
        }
        List<MissionNode> nodes = wingmen.missionRoutes().get(routeName);
        if (nodes == null) {
            send(player, WingmanText.routeNotFound(routeName));
            return;
        }
        Wingman wingman = wingmen.access().claim(player, aircraftId);
        if (wingman == null) {
            return;
        }
        wingman.autopilot().startNodeMission(nodes);
        send(player, line(ChatFormatting.GREEN, "mission.assigned", WingmanText.value(routeName),
                WingmanText.shortId(aircraftId) + "...", nodes.size()));
    }

    private static void abort(ServerPlayer player, WingmanService wingmen, UUID aircraftId) {
        if (wingmen.roster().contains(aircraftId)) {
            Wingman wingman = wingmen.access().existing(player, aircraftId);
            if (wingman == null) {
                return;
            }
            wingman.autopilot().abortMission();
        }
        send(player, line(ChatFormatting.GREEN, "mission.aborted_for", aircraftId));
    }

    private static void abortAll(ServerPlayer player, WingmanService wingmen) {
        int count = 0;
        for (Wingman wingman : wingmen.access().orderable(player)) {
            if (wingman.isAutonomous()) {
                wingman.autopilot().abortMission();
                count++;
            }
        }
        send(player, line(ChatFormatting.GREEN, "mission.aborted_all", count));
    }

    private static void status(ServerPlayer player, WingmanService wingmen) {
        boolean any = false;
        for (var entry : wingmen.roster().snapshot().entrySet()) {
            Wingman wingman = entry.getValue();
            if (!wingman.isAutonomous()) {
                continue;
            }
            any = true;
            // aircraft flying a mission order have no node list
            NodeMission mission = wingman.autopilot().mission();
            MissionNode current = mission != null ? mission.current() : null;
            Component node = current != null ? Component.literal(current.toString())
                    : Component.translatable("mcheli.wingman.mission.done");
            send(player, line(ChatFormatting.GRAY, "status.short_id", WingmanText.shortId(entry.getKey()))
                    .append(line(ChatFormatting.WHITE, "mission.status", wingman.autopilot().phase().name(),
                            mission != null ? mission.index() : 0, mission != null ? mission.size() : 0))
                    .append(line(ChatFormatting.GRAY, "mission.node", node)));
        }
        if (!any) {
            send(player, line(ChatFormatting.GRAY, "mission.none"));
        }
    }
}
