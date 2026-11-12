package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnStatus;
import com.ivomens.mcheli.api.vehicle.action.VehicleSpawner;
import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Places a vehicle the way a player placing its item does, minus the rules only players are held to. */
final class ApiVehicleSpawner implements VehicleSpawner {

    private final ServerLevel level;
    private final VehicleKind kind;
    private final String typeId;
    private final Vec3 position;
    private final Map<Integer, Entity> riders = new TreeMap<>();
    private float yaw;
    private float pitch;
    @Nullable
    private Player owner;
    private boolean respectLimits;
    private boolean requireFreeSpace = true;

    ApiVehicleSpawner(ServerLevel level, VehicleKind kind, String typeId, Vec3 position) {
        this.level = Objects.requireNonNull(level, "level");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.position = Objects.requireNonNull(position, "position");
    }

    @Override
    public VehicleSpawner rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        return this;
    }

    @Override
    public VehicleSpawner owner(@Nullable Player owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public VehicleSpawner rider(int seatId, Entity rider) {
        this.riders.put(seatId, Objects.requireNonNull(rider, "rider"));
        return this;
    }

    @Override
    public VehicleSpawner respectLimits(boolean respect) {
        this.respectLimits = respect;
        return this;
    }

    @Override
    public VehicleSpawner requireFreeSpace(boolean require) {
        this.requireFreeSpace = require;
        return this;
    }

    @Override
    public SpawnResult spawn() {
        Optional<VehicleSpec> spec = VehicleTypes.get(this.kind, this.typeId);
        if (spec.isEmpty() || !(VehicleTypes.item(spec.get()).orElse(null) instanceof AircraftItem item)) {
            return ApiSpawnResult.failed(SpawnStatus.UNKNOWN_TYPE);
        }
        if (!this.level.hasChunkAt(BlockPos.containing(this.position))) {
            return ApiSpawnResult.failed(SpawnStatus.CHUNK_NOT_LOADED);
        }
        AircraftEntity vehicle = item.createAircraft(this.level, this.position.x, this.position.y, this.position.z,
                new ItemStack(item));
        if (vehicle == null) {
            return ApiSpawnResult.failed(SpawnStatus.UNKNOWN_TYPE);
        }
        SpawnResult refusal = refusal(vehicle);
        if (refusal != null) {
            vehicle.discard();
            return refusal;
        }
        if (this.owner != null) {
            vehicle.ownership().setOwner(this.owner);
        }
        if (!this.level.addFreshEntity(vehicle)) {
            return ApiSpawnResult.failed(SpawnStatus.REFUSED);
        }
        Map<Integer, ActionResult> seated = new LinkedHashMap<>();
        this.riders.forEach((seatId, rider) -> seated.put(seatId, CrewActions.board(vehicle, rider, seatId)));
        return ApiSpawnResult.spawned(vehicle, seated);
    }

    /** Turns the new vehicle as asked, and says why it may not be placed; null when it may. */
    @Nullable
    private SpawnResult refusal(AircraftEntity vehicle) {
        vehicle.initRotationYaw(this.yaw);
        vehicle.setXRot(this.pitch);
        vehicle.xRotO = this.pitch;
        for (int seatId : this.riders.keySet()) {
            if (!vehicle.seats().isValidSeatId(seatId)) {
                return ApiSpawnResult.failed(SpawnStatus.INVALID_SEAT);
            }
        }
        if (this.requireFreeSpace && !this.level.noCollision(vehicle, vehicle.getBoundingBox().deflate(0.1))) {
            return ApiSpawnResult.failed(SpawnStatus.OBSTRUCTED);
        }
        if (this.respectLimits) {
            Optional<Component> limit = VehicleLimits.refusal(this.level, this.owner, vehicle);
            if (limit.isPresent()) {
                return ApiSpawnResult.limitReached(limit.get());
            }
        }
        return null;
    }
}
