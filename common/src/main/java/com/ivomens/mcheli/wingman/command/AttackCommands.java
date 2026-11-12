package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

import static com.ivomens.mcheli.wingman.command.WingmanCommand.asPlayer;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.send;
import static com.ivomens.mcheli.wingman.text.WingmanText.line;

/**
 * Attack orders for the wingmen of the player's aircraft ({@code engage [target]}, {@code auto},
 * {@code hold}, {@code weapon [type|clear]}) and the server's attack altitude band ({@code minalt},
 * {@code maxalt}, {@code alt clear}).
 */
final class AttackCommands {

    private static final List<String> WEAPON_TYPES = List.of("gun", "cannon", "missile", "rocket", "bomb", "torpedo",
            "machinegun1", "machinegun2", "cas", "asmissile", "aamissile", "atmissile", "tvmissile", "mkrocket",
            "targetingpod", "clear");

    private AttackCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("engage")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> engage(player, wingmen, null)))
                        .then(Commands.argument("target", UuidArgument.uuid())
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        engage(player, wingmen, UuidArgument.getUuid(ctx, "target"))))))
                .then(Commands.literal("auto").executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                        setAttackMode(player, wingmen, AttackMode.AUTO, "attack.auto"))))
                .then(Commands.literal("hold").executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                        setAttackMode(player, wingmen, AttackMode.NONE, "attack.hold"))))
                .then(Commands.literal("weapon")
                        .executes(ctx -> asPlayer(ctx, AttackCommands::showWeapon))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WEAPON_TYPES, builder))
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                        setWeapon(player, wingmen, StringArgumentType.getString(ctx, "type"))))))
                .then(altitudeLimit("minalt", true))
                .then(altitudeLimit("maxalt", false))
                .then(Commands.literal("alt")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> showAltitudes(player)))
                        .then(Commands.literal("clear").executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                clearAltitudes(player)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> altitudeLimit(String name, boolean floor) {
        return Commands.literal(name)
                .executes(ctx -> asPlayer(ctx, (player, wingmen) -> showAltitudeLimit(player, floor)))
                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                setAltitudeLimit(player, floor, DoubleArgumentType.getDouble(ctx, "y")))));
    }

    /** Manual attack on the given target, or on the player's lock without one. */
    private static void engage(ServerPlayer player, WingmanService wingmen, @Nullable UUID target) {
        List<Wingman> followers = wingmen.roster().followersOf(player.getVehicle());
        if (followers.isEmpty()) {
            send(player, line(ChatFormatting.RED, "engage.none"));
            return;
        }
        followers.forEach(w -> w.attack().set(AttackMode.MANUAL, target));
        Component shown = target != null ? Component.literal(WingmanText.shortId(target) + "...")
                : Component.translatable("mcheli.wingman.engage.player_lock");
        send(player, line(ChatFormatting.GREEN, "engage.engaging", followers.size(), shown));
    }

    private static void setAttackMode(ServerPlayer player, WingmanService wingmen, AttackMode mode, String doneKey) {
        List<Wingman> followers = wingmen.roster().followersOf(player.getVehicle());
        if (followers.isEmpty()) {
            send(player, line(ChatFormatting.RED, "attack.none"));
            return;
        }
        followers.forEach(w -> w.attack().set(mode, null));
        send(player, line(ChatFormatting.GREEN, doneKey, followers.size()));
    }

    private static void showWeapon(ServerPlayer player, WingmanService wingmen) {
        List<Wingman> followers = wingmen.roster().followersOf(player.getVehicle());
        if (followers.isEmpty()) {
            send(player, line(ChatFormatting.RED, "attack.none"));
            return;
        }
        String current = followers.get(0).attack().weaponType();
        Component shown = current != null ? WingmanText.value(current)
                : Component.translatable("mcheli.wingman.weapon.any");
        send(player, line(ChatFormatting.GRAY, "weapon.current", shown));
    }

    /** The MCHeli weapon {@code type} all wingmen of the player's aircraft use; "clear" allows any. */
    private static void setWeapon(ServerPlayer player, WingmanService wingmen, String argument) {
        List<Wingman> followers = wingmen.roster().followersOf(player.getVehicle());
        if (followers.isEmpty()) {
            send(player, line(ChatFormatting.RED, "attack.none"));
            return;
        }
        String type = argument.equalsIgnoreCase("clear") ? null : argument;
        followers.forEach(w -> w.attack().setWeaponType(type));
        send(player, type == null ? line(ChatFormatting.GREEN, "weapon.cleared")
                : line(ChatFormatting.GREEN, "weapon.set", WingmanText.value(type), followers.size()));
    }

    private static void showAltitudeLimit(ServerPlayer player, boolean floor) {
        WingmanSettings settings = WingmanConfig.get();
        double current = floor ? settings.minAttackAltitude() : settings.maxAttackAltitude();
        send(player, line(ChatFormatting.GRAY, floor ? "altitude.current_min" : "altitude.current_max",
                WingmanText.value(current)));
    }

    /** Absolute attack altitude floor or ceiling; 0 disables it. */
    private static void setAltitudeLimit(ServerPlayer player, boolean floor, double value) {
        if (!WingmanAccess.requireOperator(player)) {
            return;
        }
        if (value < 0) {
            send(player, line(ChatFormatting.RED, "altitude.negative"));
            return;
        }
        WingmanConfig.update(settings -> settings.with(
                floor ? WingmanSettings.MIN_ATTACK_ALTITUDE : WingmanSettings.MAX_ATTACK_ALTITUDE, value));
        String limit = floor ? "min" : "max";
        send(player, value == 0 ? line(ChatFormatting.GREEN, "altitude." + limit + "_cleared")
                : line(ChatFormatting.GREEN, "altitude." + limit + "_set", WingmanText.value("Y=" + value)));
    }

    private static void showAltitudes(ServerPlayer player) {
        WingmanSettings settings = WingmanConfig.get();
        send(player, line(ChatFormatting.GRAY, "altitude.current", WingmanText.value(settings.minAttackAltitude()),
                WingmanText.value(settings.maxAttackAltitude())));
    }

    private static void clearAltitudes(ServerPlayer player) {
        if (!WingmanAccess.requireOperator(player)) {
            return;
        }
        WingmanConfig.update(settings -> settings.withAttackAltitudes(0.0, 0.0));
        send(player, line(ChatFormatting.GREEN, "altitude.cleared"));
    }
}
