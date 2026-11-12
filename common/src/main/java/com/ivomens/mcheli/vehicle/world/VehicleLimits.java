package com.ivomens.mcheli.vehicle.world;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.AdminAccess;
import com.ivomens.mcheli.config.AdminSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's limits on placing vehicles ({@code MaxVehiclesPerPlayer}, {@code MaxVehiclesPerChunk},
 * {@code MaxVehiclesPerLevel} and one per vehicle kind). They count the vehicles in loaded chunks, through
 * each level's {@link AircraftIndex}, so a check costs a walk over the vehicles and never over every
 * entity. Operators may bypass them ({@code OpsBypassLimits}).
 */
public final class VehicleLimits {

    private VehicleLimits() {
    }

    /**
     * Why the vehicle may not be placed where it stands, or empty when it may.
     *
     * @param placer the player placing it; null for a dispenser, which only the per-player limit spares
     */
    public static Optional<Component> refusal(ServerLevel level, @Nullable Player placer, AircraftEntity vehicle) {
        AdminSettings settings = McheliConfig.admin();
        if (placer != null && settings.opsBypassLimits() && AdminAccess.isAdmin(placer)) {
            return Optional.empty();
        }
        AdminSettings.Limits limits = settings.limits();
        if (placer != null && limits.perPlayer() > 0 && ownedBy(level, placer.getUUID()) >= limits.perPlayer()) {
            return refuse("player", limits.perPlayer());
        }
        AircraftIndex index = AircraftIndex.of(level);
        if (limits.perChunk() > 0 && index.countInChunk(SectionPos.blockToSectionCoord(vehicle.getX()),
                SectionPos.blockToSectionCoord(vehicle.getZ())) >= limits.perChunk()) {
            return refuse("chunk", limits.perChunk());
        }
        if (limits.perLevel() > 0 && index.aircraft().size() >= limits.perLevel()) {
            return refuse("level", limits.perLevel());
        }
        int kindLimit = limitOf(limits, vehicle.kind());
        if (kindLimit > 0 && countOfKind(index, vehicle.kind()) >= kindLimit) {
            return Optional.of(Component.translatable("message.mcheli.limit.kind",
                    Component.translatable("mcheli.kind." + vehicle.kind().directory()), kindLimit));
        }
        return Optional.empty();
    }

    /** The limit config for the kind; 0 means unlimited. */
    public static int limitOf(AdminSettings.Limits limits, VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> limits.helicopters();
            case PLANE -> limits.planes();
            case SHIP -> limits.ships();
            case TANK -> limits.tanks();
            case VEHICLE -> limits.groundVehicles();
        };
    }

    /** Vehicles the player owns in the loaded chunks of every level of the server. */
    public static int ownedBy(ServerLevel anyLevel, UUID owner) {
        int count = 0;
        for (ServerLevel level : anyLevel.getServer().getAllLevels()) {
            for (AircraftEntity aircraft : AircraftIndex.of(level).aircraft()) {
                if (!aircraft.isRemoved() && aircraft.ownership().owner().filter(owner::equals).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countOfKind(AircraftIndex index, VehicleKind kind) {
        int count = 0;
        for (AircraftEntity aircraft : index.aircraft()) {
            if (aircraft.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    private static Optional<Component> refuse(String limit, int max) {
        return Optional.of(Component.translatable("message.mcheli.limit." + limit, max));
    }
}
