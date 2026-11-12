package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.brain.OrderProgress;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.ivomens.mcheli.wingman.util.WingmanAircraft;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.ivomens.mcheli.wingman.command.WingmanCommand.asPlayer;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.send;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.usage;
import static com.ivomens.mcheli.wingman.text.WingmanText.line;

/**
 * Mission orders: {@code order dispatch <aircraft> <baseId> <types> <targetX> <targetZ> [orbitR] [alt]
 * [passes] [seconds]}, {@code order abort [aircraft]}, {@code order status} and
 * {@code order park <aircraft> <parkingId>}, which assigns the parking spot an order starts from.
 */
final class OrderCommands {

    private static final String DISPATCH_USAGE = "/wingman order dispatch <uuid> <baseId> <types> <targetX> <targetZ>"
            + " [orbitR] [alt] [passes] [seconds]";
    /** The optional dispatch values, in order; each one missing keeps the planner's default. */
    private static final List<String> OPTIONAL_VALUES = List.of("orbitR", "alt", "passes", "seconds");

    private OrderCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("order")
                .executes(ctx -> usage(ctx, "/wingman order <dispatch|abort|status|park> [args]"))
                .then(Commands.literal("dispatch")
                        .executes(ctx -> usage(ctx, DISPATCH_USAGE))
                        .then(Commands.argument("aircraft", UuidArgument.uuid())
                                .then(Commands.argument("baseId", StringArgumentType.word())
                                        .then(Commands.argument("types", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(MissionType.values()).map(Enum::name), builder))
                                                .then(Commands.argument("targetX", DoubleArgumentType.doubleArg())
                                                        .then(dispatchTail()))))))
                .then(Commands.literal("abort")
                        .executes(ctx -> asPlayer(ctx, OrderCommands::abortAll))
                        .then(Commands.argument("aircraft", UuidArgument.uuid())
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        abort(player, wingmen, UuidArgument.getUuid(ctx, "aircraft"))))))
                .then(Commands.literal("status").executes(ctx -> asPlayer(ctx, OrderCommands::status)))
                .then(Commands.literal("park")
                        .executes(ctx -> usage(ctx, "/wingman order park <uuid> <parkingId>"))
                        .then(Commands.argument("aircraft", UuidArgument.uuid())
                                .then(Commands.argument("parkingId", StringArgumentType.word())
                                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> park(player, wingmen,
                                                UuidArgument.getUuid(ctx, "aircraft"),
                                                StringArgumentType.getString(ctx, "parkingId"))))))));
    }

    /** {@code <targetZ> [orbitR] [alt] [passes] [seconds]}, each optional value executable on its own. */
    private static ArgumentBuilder<CommandSourceStack, ?> dispatchTail() {
        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = OPTIONAL_VALUES.size() - 1; i >= 0; i--) {
            String name = OPTIONAL_VALUES.get(i);
            ArgumentType<?> type = name.equals("passes") || name.equals("seconds")
                    ? IntegerArgumentType.integer() : DoubleArgumentType.doubleArg();
            ArgumentBuilder<CommandSourceStack, ?> value = Commands.argument(name, type)
                    .executes(OrderCommands::dispatch);
            tail = tail == null ? value : value.then(tail);
        }
        return Commands.argument("targetZ", DoubleArgumentType.doubleArg())
                .executes(OrderCommands::dispatch)
                .then(tail);
    }

    private static int dispatch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return asPlayer(ctx, (player, wingmen) -> dispatch(player, wingmen, ctx));
    }

    private static void dispatch(ServerPlayer player, WingmanService wingmen, CommandContext<CommandSourceStack> ctx) {
        UUID aircraftId = UuidArgument.getUuid(ctx, "aircraft");
        Entity aircraft = player.serverLevel().getEntity(aircraftId);
        if (aircraft == null) {
            send(player, WingmanText.entityNotFound());
            return;
        }
        String types = StringArgumentType.getString(ctx, "types");
        MissionOrder.Builder order = MissionOrder.builder().baseId(StringArgumentType.getString(ctx, "baseId"));
        for (String type : types.split(",")) {
            try {
                order.type(MissionType.valueOf(type.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown types are skipped; an order with none at all is refused below
            }
        }
        if (!order.hasTypes()) {
            send(player, line(ChatFormatting.RED, "order.no_types_in", types));
            return;
        }
        order.target(DoubleArgumentType.getDouble(ctx, "targetX"), DoubleArgumentType.getDouble(ctx, "targetZ"))
                .orbitRadius(optional(ctx, "orbitR", MissionOrder.DEFAULT_ORBIT_RADIUS))
                .cruiseAlt(optional(ctx, "alt", MissionOrder.DEFAULT_CRUISE_ALT))
                .strikePasses(optional(ctx, "passes", MissionOrder.DEFAULT_STRIKE_PASSES))
                .timeLimitSeconds(optional(ctx, "seconds", MissionOrder.DEFAULT_TIME_LIMIT_SECONDS))
                .defaultWeapons();

        Wingman wingman = wingmen.access().claim(player, aircraftId);
        if (wingman == null) {
            return;
        }
        // a freshly spawned aircraft has no fuel and would return to base as soon as it arrives
        WingmanAircraft.fillFuel(aircraft);
        MissionOrder built = order.build();
        wingman.startOrder(built);
        wingman.attack().setWeaponType(built.primaryWeapon());

        send(player, line(ChatFormatting.GREEN, "order.dispatched", WingmanText.value(types),
                WingmanText.shortId(aircraftId) + "...")
                .append(line(ChatFormatting.GRAY, "order.dispatched_details", built.baseId(), (int) built.targetX(),
                        (int) built.targetZ())));
    }

    /** An optional dispatch value, or the default when the command line stops before it. */
    private static <T> T optional(CommandContext<CommandSourceStack> ctx, String name, T fallback) {
        try {
            @SuppressWarnings("unchecked")
            T value = (T) ctx.getArgument(name, fallback.getClass());
            return value;
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }

    private static void abort(ServerPlayer player, WingmanService wingmen, UUID aircraftId) {
        if (wingmen.roster().contains(aircraftId)) {
            Wingman wingman = wingmen.access().existing(player, aircraftId);
            if (wingman == null) {
                return;
            }
            wingman.autopilot().abortOrder();
        }
        send(player, line(ChatFormatting.GREEN, "order.aborted_for", WingmanText.shortId(aircraftId)));
    }

    private static void abortAll(ServerPlayer player, WingmanService wingmen) {
        int count = 0;
        for (Wingman wingman : wingmen.access().orderable(player)) {
            if (wingman.autopilot().hasOrder()) {
                wingman.autopilot().abortOrder();
                count++;
            }
        }
        send(player, line(ChatFormatting.GREEN, "order.aborted_all", count));
    }

    private static void status(ServerPlayer player, WingmanService wingmen) {
        boolean any = false;
        for (var entry : wingmen.roster().snapshot().entrySet()) {
            OrderProgress progress = entry.getValue().autopilot().order();
            if (progress == null) {
                continue;
            }
            any = true;
            String rtb = progress.rtbReason().isEmpty() ? "-" : progress.rtbReason();
            send(player, line(ChatFormatting.GRAY, "status.short_id", WingmanText.shortId(entry.getKey()))
                    .append(line(ChatFormatting.WHITE, "order.status", entry.getValue().autopilot().phase().name()))
                    .append(line(ChatFormatting.GRAY, "order.status_details", progress.order().missionTypes(),
                            progress.timer() / 20, rtb)));
        }
        if (!any) {
            send(player, line(ChatFormatting.GRAY, "order.none"));
        }
    }

    /** Assigns the parking spot the next order starts from. */
    private static void park(ServerPlayer player, WingmanService wingmen, UUID aircraftId, String parkingId) {
        Wingman wingman = wingmen.access().claim(player, aircraftId);
        if (wingman == null) {
            return;
        }
        wingman.autopilot().parkAt(parkingId);
        send(player, line(ChatFormatting.GREEN, "order.parking_assigned", WingmanText.value(parkingId),
                WingmanText.shortId(aircraftId)));
    }
}
