package com.ivomens.mcheli.wingman.airfield;

import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The markers of one level, saved as {@code data/wingman_markers.dat}. The file format is the one
 * every earlier version wrote: a {@code markers} list of compounds with {@code x}, {@code y},
 * {@code z}, {@code type}, {@code id}, {@code baseId} and, for parking spots with a heading,
 * {@code parkingHeading}.
 */
final class MarkerStore extends SavedData {

    private static final String FILE = "wingman_markers";
    private static final int ANY_HEADING = -1;

    private static final SavedData.Factory<MarkerStore> FACTORY =
            new SavedData.Factory<>(MarkerStore::new, MarkerStore::load, null);

    /** In placement order, which is the order lookups return the first match in. */
    private final List<Marker> markers = new ArrayList<>();

    static MarkerStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    List<Marker> markers() {
        return Collections.unmodifiableList(this.markers);
    }

    /** Puts the marker at its position, replacing whatever marker was there. */
    void put(Marker marker) {
        this.markers.removeIf(m -> m.isAt(marker.blockX(), marker.blockY(), marker.blockZ()));
        this.markers.add(marker);
        setDirty();
    }

    void remove(BlockPos pos) {
        if (this.markers.removeIf(m -> m.isAt(pos.getX(), pos.getY(), pos.getZ()))) {
            setDirty();
        }
    }

    void rename(BlockPos pos, String id) {
        for (int i = 0; i < this.markers.size(); i++) {
            Marker m = this.markers.get(i);
            if (m.isAt(pos.getX(), pos.getY(), pos.getZ())) {
                this.markers.set(i, m.withId(id));
                setDirty();
                return;
            }
        }
    }

    private static MarkerStore load(CompoundTag tag, HolderLookup.Provider registries) {
        MarkerStore store = new MarkerStore();
        ListTag list = tag.getList("markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String baseId = c.contains("baseId") ? c.getString("baseId") : "";
            int parkingHeading = c.contains("parkingHeading") ? c.getInt("parkingHeading") : ANY_HEADING;
            store.markers.add(new Marker(c.getInt("x"), c.getInt("y"), c.getInt("z"), readType(c), c.getString("id"),
                    baseId, parkingHeading));
        }
        return store;
    }

    /** Unknown type names (from a newer version) load as parking spots. */
    private static MarkerType readType(CompoundTag c) {
        try {
            return MarkerType.valueOf(c.getString("type"));
        } catch (IllegalArgumentException e) {
            return MarkerType.PARKING;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Marker m : this.markers) {
            CompoundTag c = new CompoundTag();
            c.putInt("x", m.blockX());
            c.putInt("y", m.blockY());
            c.putInt("z", m.blockZ());
            c.putString("type", m.type().name());
            c.putString("id", m.id());
            c.putString("baseId", m.baseId());
            if (m.parkingHeading() >= 0) {
                c.putInt("parkingHeading", m.parkingHeading());
            }
            list.add(c);
        }
        tag.put("markers", list);
        return tag;
    }
}
