package com.ivomens.mcheli.world.uavstation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The UAVs a station is paired with, in pairing order; saved as {@code PairedUavs}. */
final class UavPairings {

    private final Set<UUID> uavs = new LinkedHashSet<>();

    List<UUID> list() {
        return Collections.unmodifiableList(new ArrayList<>(this.uavs));
    }

    /** The live set, for writing back to a tablet. */
    Set<UUID> ids() {
        return this.uavs;
    }

    boolean contains(UUID uav) {
        return uav != null && this.uavs.contains(uav);
    }

    void add(UUID uav) {
        this.uavs.add(uav);
    }

    void addAll(Collection<UUID> uavs) {
        this.uavs.addAll(uavs);
    }

    boolean remove(UUID uav) {
        return this.uavs.remove(uav);
    }

    void save(CompoundTag nbt) {
        ListTag paired = new ListTag();
        for (UUID id : this.uavs) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("ID", id);
            paired.add(entry);
        }
        nbt.put("PairedUavs", paired);
    }

    void load(CompoundTag nbt) {
        this.uavs.clear();
        ListTag paired = nbt.getList("PairedUavs", Tag.TAG_COMPOUND);
        for (int i = 0; i < paired.size(); i++) {
            CompoundTag entry = paired.getCompound(i);
            if (entry.hasUUID("ID")) {
                this.uavs.add(entry.getUUID("ID"));
            }
        }
    }
}
