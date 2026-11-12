package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import static com.ivomens.mcheli.wingman.command.WingmanCommand.asPlayer;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.send;
import static com.ivomens.mcheli.wingman.text.WingmanText.line;

/** {@code follow [aircraft]}, {@code stop}, {@code status}, {@code dist <side> <alt> <rear>}, {@code maxwings <n>}. */
final class FormationCommands {

    private static final double SEARCH_RANGE_SQ = 512.0 * 512.0;
    private static final int MAX_WINGMEN_LIMIT = 64;

    private FormationCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("follow")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> follow(player, wingmen, null)))
                        .then(Commands.argument("aircraft", UuidArgument.uuid())
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        follow(player, wingmen, UuidArgument.getUuid(ctx, "aircraft"))))))
                .then(Commands.literal("stop").executes(ctx -> asPlayer(ctx, FormationCommands::stop)))
                .then(Commands.literal("status").executes(ctx -> asPlayer(ctx, FormationCommands::status)))
                .then(Commands.literal("dist")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> showDistances(player)))
                        .then(Commands.argument("side", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("altitude", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("rear", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> asPlayer(ctx, (player, wingmen) -> setDistances(player,
                                                        DoubleArgumentType.getDouble(ctx, "side"),
                                                        DoubleArgumentType.getDouble(ctx, "altitude"),
                                                        DoubleArgumentType.getDouble(ctx, "rear"))))))))
                .then(Commands.literal("maxwings")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> send(player, line(ChatFormatting.GRAY,
                                "maxwings.current", WingmanConfig.get().maxWingmen()))))
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        setMaxWingmen(player, IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    /** Adds the given or the nearest unassigned aircraft as a wingman of the player's aircraft. */
    private static void follow(ServerPlayer player, WingmanService wingmen, @Nullable UUID aircraftId) {
        Entity leader = player.getVehicle();
        if (!(leader instanceof AircraftEntity)) {
            send(player, line(ChatFormatting.RED, "follow.not_in_aircraft"));
            return;
        }
        int slot = wingmen.roster().countFollowing(leader);
        int max = WingmanConfig.get().maxWingmen();
        if (slot >= max) {
            send(player, line(ChatFormatting.RED, "follow.max_wingmen", max));
            return;
        }
        Entity aircraft = aircraftId != null ? player.serverLevel().getEntity(aircraftId)
                : nearestUnassignedAircraft(player, wingmen.roster(), leader);
        if (aircraft == null) {
            send(player, aircraftId != null ? line(ChatFormatting.RED, "error.entity_not_found_id", aircraftId)
                    : line(ChatFormatting.RED, "follow.none_nearby"));
            return;
        }
        Component refusal = refusal(player, leader, aircraft);
        if (refusal != null) {
            send(player, refusal);
            return;
        }
        if (wingmen.access().follow(player, aircraft.getUUID(), leader, slot)) {
            send(player, line(ChatFormatting.GREEN, "follow.assigned", slot,
                    WingmanText.shortId(aircraft.getUUID()) + "..."));
        }
    }

    /** Why this aircraft cannot follow the player's, or null if it can. */
    private static @Nullable Component refusal(ServerPlayer player, Entity leader, Entity aircraft) {
        if (!(aircraft instanceof AircraftEntity)) {
            return line(ChatFormatting.RED, "follow.not_aircraft");
        }
        if (aircraft == leader) {
            return line(ChatFormatting.RED, "follow.own_aircraft");
        }
        // also catches the player's aircraft reached through a seat or hitbox entity
        if (aircraft.hasIndirectPassenger(player) || player.hasIndirectPassenger(aircraft)) {
            return line(ChatFormatting.RED, "follow.own_aircraft_wingman");
        }
        return null;
    }

    private static @Nullable Entity nearestUnassignedAircraft(ServerPlayer player, Roster roster, Entity leader) {
        double bestSq = SEARCH_RANGE_SQ;
        Entity best = null;
        for (AircraftEntity aircraft : AircraftIndex.of(player.serverLevel()).aircraft()) {
            if (aircraft == leader || aircraft.isRemoved() || roster.contains(aircraft.getUUID())) {
                continue;
            }
            double distSq = leader.distanceToSqr(aircraft);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = aircraft;
            }
        }
        return best;
    }

    private static void stop(ServerPlayer player, WingmanService wingmen) {
        Entity leader = player.getVehicle();
        if (!(leader instanceof AircraftEntity)) {
            send(player, line(ChatFormatting.RED, "stop.not_in_aircraft"));
            return;
        }
        int before = wingmen.roster().countFollowing(leader);
        wingmen.roster().releaseFollowersOf(leader);
        send(player, line(ChatFormatting.GREEN, "stop.stopped", before));
    }

    private static void status(ServerPlayer player, WingmanService wingmen) {
        Map<UUID, Wingman> all = wingmen.roster().snapshot();
        if (all.isEmpty()) {
            send(player, line(ChatFormatting.GRAY, "status.none"));
            return;
        }
        WingmanSettings settings = WingmanConfig.get();
        send(player, line(ChatFormatting.YELLOW, "status.header", settings.formationSideDist(),
                settings.formationAltOffset(), settings.formationRearDist()));
        all.forEach((id, wingman) -> send(player, statusLine(id, wingman)));
    }

    private static Component statusLine(UUID id, Wingman wingman) {
        String leader = wingman.leader() != null ? wingman.leader().getClass().getSimpleName() : "none";
        String attack = "";
        if (wingman.attack().isAttacking()) {
            UUID target = wingman.attack().manualTarget();
            attack = " atk=" + wingman.attack().mode().code()
                    + (target != null ? ":" + WingmanText.shortId(target) : "");
        }
        return line(ChatFormatting.GRAY, "status.id", WingmanText.shortId(id)).append(line(ChatFormatting.WHITE,
                "status.line", wingman.slot(), wingman.statusName(), leader, attack));
    }

    private static void showDistances(ServerPlayer player) {
        WingmanSettings settings = WingmanConfig.get();
        send(player, line(ChatFormatting.GRAY, "dist.usage", settings.formationSideDist(),
                settings.formationAltOffset(), settings.formationRearDist()));
    }

    /** Formation distances, until the next config reload. */
    private static void setDistances(ServerPlayer player, double side, double alt, double rear) {
        if (!WingmanAccess.requireOperator(player)) {
            return;
        }
        if (side < 0 || rear < 0) {
            send(player, line(ChatFormatting.RED, "dist.negative"));
            return;
        }
        WingmanConfig.update(settings -> settings.withFormation(side, alt, rear));
        send(player, line(ChatFormatting.GREEN, "dist.set", side, alt, rear));
    }

    private static void setMaxWingmen(ServerPlayer player, int max) {
        if (!WingmanAccess.requireOperator(player)) {
            return;
        }
        if (max < 1 || max > MAX_WINGMEN_LIMIT) {
            send(player, line(ChatFormatting.RED, "maxwings.range"));
            return;
        }
        WingmanConfig.update(settings -> settings.with(WingmanSettings.MAX_WINGMEN, max));
        send(player, line(ChatFormatting.GREEN, "maxwings.set", max));
    }
}
