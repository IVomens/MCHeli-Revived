package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.component.OwnershipComponent;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@code /mcheli vehicles}: {@code list [player]} lists the vehicles in loaded chunks (of one owner, or all)
 * with their positions; {@code remove <targets>}, {@code remove radius <blocks>} and
 * {@code removeall <kind> confirm} remove vehicles through {@link VehicleRemoval}. Vehicles in unloaded
 * chunks are neither listed nor removed.
 */
final class VehicleCommands {

    /** Lines printed by {@code list} before the rest is only counted. */
    static final int LIST_LIMIT = 50;
    private static final double MAX_RADIUS = 512.0;

    private VehicleCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> vehicles() {
        return Commands.literal("vehicles")
                .then(Commands.literal("list")
                        .executes(c -> list(c.getSource(), vehicle -> true))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        c.getSource().getOnlinePlayerNames(), b))
                                .executes(c -> list(c.getSource(), ownedBy(c.getSource(),
                                        StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("radius")
                                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, MAX_RADIUS))
                                        .executes(VehicleCommands::removeInRadius)))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(VehicleCommands::removeTargets)))
                .then(Commands.literal("removeall")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kindNames(), b))
                                .then(Commands.literal("confirm").executes(VehicleCommands::removeAll))));
    }

    /**
     * Vehicles of the named player: by UUID when the player is online or known to the server, and by the
     * name the owner had when they placed or locked the vehicle otherwise.
     */
    private static Predicate<AircraftEntity> ownedBy(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        GameProfileCache cache = source.getServer().getProfileCache();
        UUID id = online != null ? online.getUUID()
                : cache != null ? cache.get(name).map(GameProfile::getId).orElse(null) : null;
        return vehicle -> {
            OwnershipComponent ownership = vehicle.ownership();
            return id != null ? ownership.owner().filter(id::equals).isPresent()
                    : ownership.hasOwner() && ownership.ownerName().equalsIgnoreCase(name);
        };
    }

    /** Every vehicle in the loaded chunks of the server's levels that passes the filter. */
    static List<AircraftEntity> loaded(CommandSourceStack source, Predicate<AircraftEntity> filter) {
        List<AircraftEntity> found = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (AircraftEntity vehicle : AircraftIndex.of(level).aircraft()) {
                if (!vehicle.isRemoved() && filter.test(vehicle)) {
                    found.add(vehicle);
                }
            }
        }
        return found;
    }

    private static int list(CommandSourceStack source, Predicate<AircraftEntity> filter) {
        List<AircraftEntity> vehicles = loaded(source, filter);
        source.sendSuccess(() -> AdminText.text("vehicles.list.header", vehicles.size()), false);
        for (int i = 0; i < Math.min(vehicles.size(), LIST_LIMIT); i++) {
            AircraftEntity vehicle = vehicles.get(i);
            source.sendSuccess(() -> AdminText.describe(vehicle).append(" ").append(AdminText.position(vehicle)),
                    false);
        }
        if (vehicles.size() > LIST_LIMIT) {
            source.sendSuccess(() -> AdminText.text("vehicles.list.more", vehicles.size() - LIST_LIMIT), false);
        }
        return vehicles.size();
    }

    private static int removeTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Set<AircraftEntity> vehicles = new LinkedHashSet<>();
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            AircraftEntity vehicle = vehicleOf(entity);
            if (vehicle != null) {
                vehicles.add(vehicle);
            }
        }
        return remove(context.getSource(), vehicles);
    }

    private static int removeInRadius(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double radius = DoubleArgumentType.getDouble(context, "blocks");
        Vec3 center = source.getPosition();
        ServerLevel level = source.getLevel();
        return remove(source, loaded(source, vehicle -> vehicle.level() == level
                && vehicle.position().distanceToSqr(center) <= radius * radius));
    }

    private static int removeAll(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "kind").toLowerCase(Locale.ROOT);
        VehicleKind kind = Arrays.stream(VehicleKind.values())
                .filter(candidate -> kindName(candidate).equals(name)).findFirst().orElse(null);
        if (kind == null && !name.equals("all")) {
            context.getSource().sendFailure(AdminText.error("vehicles.unknown_kind", name,
                    String.join(", ", kindNames())));
            return 0;
        }
        return remove(context.getSource(), loaded(context.getSource(), vehicle -> kind == null
                || vehicle.kind() == kind));
    }

    private static int remove(CommandSourceStack source, Collection<AircraftEntity> vehicles) {
        int removed = 0;
        for (AircraftEntity vehicle : vehicles) {
            if (VehicleRemoval.remove(vehicle, source.getTextName())) {
                removed++;
            }
        }
        int count = removed;
        source.sendSuccess(() -> AdminText.text("vehicles.removed", count), true);
        return removed;
    }

    @Nullable
    private static AircraftEntity vehicleOf(Entity entity) {
        return switch (entity) {
            case AircraftEntity vehicle -> vehicle;
            case SeatEntity seat -> seat.getParent();
            case HitBoxEntity hitBox -> hitBox.owner();
            default -> null;
        };
    }

    /** {@code helicopter}, {@code plane}, {@code ship}, {@code tank} or {@code vehicle}. */
    static String kindName(VehicleKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static List<String> kindNames() {
        List<String> names = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            names.add(kindName(kind));
        }
        names.add("all");
        return names;
    }
}
