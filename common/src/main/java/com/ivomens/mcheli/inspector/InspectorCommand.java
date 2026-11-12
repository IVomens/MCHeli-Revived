package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.inspector.net.InspectorRequestHandler;
import com.ivomens.mcheli.inspector.net.InspectorRequestMessage;
import com.ivomens.mcheli.inspector.net.OpenInspectorMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.networking.Network;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * {@code /mcheli inspect [vehicle]}: opens the vehicle inspector (docs/INSPECTOR.md) on the operator's client,
 * on the named vehicle, else the one they are aboard, else the one they look at. The inspector's own key
 * opens it the same way without the command; the command is for operators on a server, whose inspector then
 * also shows the server's view.
 */
public final class InspectorCommand {

    private InspectorCommand() {
    }

    /** Adds {@code inspect} to the {@code /mcheli} command. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcheli").then(Commands.literal("inspect")
                .requires(source -> source.hasPermission(InspectorRequestHandler.OPERATOR))
                .executes(context -> open(context.getSource(), null))
                .then(Commands.argument("vehicle", EntityArgument.entity())
                        .executes(context -> open(context.getSource(), EntityArgument.getEntity(context, "vehicle"))))));
    }

    /** Declares the inspector's messages, with the server's answer to a snapshot request. */
    public static void registerMessages(MessageRegistry registry) {
        registry.serverbound(InspectorRequestMessage.TYPE, new InspectorRequestHandler(Network::sendToPlayer));
        registry.clientbound(InspectorReplyMessage.TYPE);
        registry.clientbound(OpenInspectorMessage.TYPE);
    }

    private static int open(CommandSourceStack source, @Nullable Entity named) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AircraftEntity vehicle = named != null ? InspectorTargets.vehicleOf(named) : InspectorTargets.find(player);
        if (vehicle == null) {
            source.sendFailure(Component.translatableWithFallback("commands.mcheli.inspect.no_vehicle",
                    "No vehicle to inspect: get in one, look at one or name one."));
            return 0;
        }
        Network.sendToPlayer(player, new OpenInspectorMessage(vehicle.getId()));
        return 1;
    }
}
