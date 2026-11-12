package com.ivomens.mcheli.vehicle.uav;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Last known chunk of every UAV, per dimension, so a station can tell where a UAV that is not loaded
 * was left.
 * <p>
 * Lookups never load terrain: {@link #locateUAV} only returns a UAV that is already loaded and callers
 * treat {@code null} as "out of range / unloaded". UAVs that are actively flown are kept loaded by the
 * chunk streamer, so this only affects parked UAVs.
 */
public final class UAVTracker {

    private UAVTracker() {
    }

    /** Chunk of the UAV: its current chunk when loaded, else the last saved one (or null). */
    @Nullable
    public static ChunkPos getUAVPos(@NotNull Level world, @NotNull UUID id) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return null;
        }
        Entity uav = serverWorld.getEntity(id);
        if (uav != null) {
            return uav.chunkPosition();
        }
        return getLastKnownPos(world, id);
    }

    /** Last saved chunk of the UAV, without looking at loaded entities. */
    @Nullable
    public static ChunkPos getLastKnownPos(@NotNull Level world, @NotNull UUID id) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return null;
        }
        return GlobalUavData.get(serverWorld).entityLocations.get(id);
    }

    /**
     * Resolves a loaded UAV entity by UUID. Returns {@code null} when the UAV is not loaded (or does
     * not exist); use {@link #getLastKnownPos} to report where it was left.
     */
    @Nullable
    public static AircraftEntity locateUAV(@NotNull Level world, @NotNull UUID id) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return null;
        }
        Entity found = serverWorld.getEntity(id);
        return found instanceof AircraftEntity ac && !ac.isRemoved() ? ac : null;
    }

    public static void saveUAVPos(@NotNull Level world, @NotNull AircraftEntity aircraft) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        GlobalUavData data = GlobalUavData.get(serverWorld);
        ChunkPos pos = aircraft.chunkPosition();
        ChunkPos old = data.entityLocations.put(aircraft.getUUID(), pos);
        if (!pos.equals(old)) {
            data.setDirty();
        }
    }

    public static void delUAVPos(@NotNull Level world, @NotNull AircraftEntity aircraft) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        GlobalUavData data = GlobalUavData.get(serverWorld);
        if (data.entityLocations.remove(aircraft.getUUID()) != null) {
            data.setDirty();
        }
    }

    /** Saved under the historical name so existing worlds keep their UAV positions. */
    public static class GlobalUavData extends SavedData {
        private static final String DATA_NAME = "MCH_GlobalUAVData";
        private static final SavedData.Factory<GlobalUavData> FACTORY =
                new SavedData.Factory<>(GlobalUavData::new, GlobalUavData::load, null);
        protected final Map<UUID, ChunkPos> entityLocations = new HashMap<>();

        public static GlobalUavData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        }

        public static GlobalUavData load(CompoundTag nbt, HolderLookup.Provider registries) {
            GlobalUavData data = new GlobalUavData();
            ListTag list = nbt.getList("LocationList", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.hasUUID("ID")) {
                    continue;
                }
                data.entityLocations.put(entry.getUUID("ID"), new ChunkPos(entry.getInt("CX"), entry.getInt("CZ")));
            }
            return data;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, ChunkPos> entry : this.entityLocations.entrySet()) {
                CompoundTag compound = new CompoundTag();
                compound.putUUID("ID", entry.getKey());
                compound.putInt("CX", entry.getValue().x);
                compound.putInt("CZ", entry.getValue().z);
                list.add(compound);
            }
            nbt.put("LocationList", list);
            return nbt;
        }
    }
}
