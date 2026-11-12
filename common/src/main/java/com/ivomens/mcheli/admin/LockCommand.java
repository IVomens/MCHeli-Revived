package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPicking;
import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.entityview.Riders;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.config.AdminAccess;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.vehicle.component.OwnershipComponent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * {@code /mcheli lock} and {@code /mcheli unlock}: on the vehicle the player is aboard, or else the one they
 * look at. Its owner and operators may change the lock. A vehicle without an owner (saved before owners were
 * recorded, or placed by a dispenser) becomes the vehicle of whoever locks it while flying it, or of the
 * operator who locks it.
 */
final class LockCommand {

    private LockCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> lock() {
        return Commands.literal("lock").executes(context -> run(context.getSource(), true));
    }

    static LiteralArgumentBuilder<CommandSourceStack> unlock() {
        return Commands.literal("unlock").executes(context -> run(context.getSource(), false));
    }

    private static int run(CommandSourceStack source, boolean lock) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!McheliConfig.admin().vehicleLocking()) {
            throw failure("lock.disabled");
        }
        AircraftEntity vehicle = target(player);
        if (vehicle == null) {
            throw failure("lock.no_vehicle");
        }
        OwnershipComponent ownership = vehicle.ownership();
        if (!ownership.hasOwner()) {
            if (!lock) {
                throw failure("lock.not_locked");
            }
            if (vehicle.getRiddenByEntity() != player && !AdminAccess.isAdmin(player)) {
                throw failure("lock.no_owner");
            }
            ownership.setOwner(player);
        }
        if (!ownership.mayChangeLock(player)) {
            throw failure("lock.not_owner", ownership.ownerName());
        }
        ownership.setLocked(lock);
        source.sendSuccess(() -> OwnershipComponent.lockChanged(lock).copy().append(" ")
                .append(AdminText.describe(vehicle)), false);
        return 1;
    }

    /** The vehicle the player is aboard or, failing that, looks at. */
    @Nullable
    static AircraftEntity target(ServerPlayer player) {
        Vehicle aboard = Riders.vehicleOf(player);
        if (aboard != null && aboard.entity() instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        Entity looked = AircraftPicking.lookedAt(player);
        return switch (looked) {
            case AircraftEntity aircraft -> aircraft;
            case SeatEntity seat -> seat.getParent();
            case HitBoxEntity hitBox -> hitBox.owner();
            case null, default -> null;
        };
    }

    private static CommandSyntaxException failure(String key, Object... args) {
        Component message = AdminText.text(key, args);
        return new SimpleCommandExceptionType(message).create();
    }
}
