package com.ivomens.mcheli.wingman.airfield;

import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The taxi routes of one level by route id, saved as {@code data/wingman_taxi_routes.dat} in the
 * format every earlier version wrote. Keys added later ({@code runwayBId}, {@code parkingHeading},
 * {@code arrivalWaypoints}, {@code arrivalRunwayId}, {@code owner}) are optional when reading.
 */
final class TaxiRouteStore extends SavedData {

    private static final String FILE = "wingman_taxi_routes";

    private static final SavedData.Factory<TaxiRouteStore> FACTORY =
            new SavedData.Factory<>(TaxiRouteStore::new, TaxiRouteStore::load, null);

    /** In the order they were first saved, which is the order lookups return the first match in. */
    private final Map<String, TaxiRoute> routes = new LinkedHashMap<>();

    static TaxiRouteStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    Collection<TaxiRoute> routes() {
        return Collections.unmodifiableCollection(this.routes.values());
    }

    TaxiRoute get(String routeId) {
        return this.routes.get(routeId);
    }

    void put(TaxiRoute route) {
        this.routes.put(route.routeId(), route);
        setDirty();
    }

    void remove(String routeId) {
        if (this.routes.remove(routeId) != null) {
            setDirty();
        }
    }

    private static TaxiRouteStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TaxiRouteStore store = new TaxiRouteStore();
        ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String runwayBId = c.contains("runwayBId") ? c.getString("runwayBId") : "";
            int parkingHeading = c.contains("parkingHeading") ? c.getInt("parkingHeading") : TaxiRoute.HEADING_ANY;
            List<String> arrivalWaypoints = c.contains("arrivalWaypoints")
                    ? readStrings(c, "arrivalWaypoints") : new ArrayList<>();
            String arrivalRunwayId = c.contains("arrivalRunwayId") ? c.getString("arrivalRunwayId") : "";
            TaxiRoute route = new TaxiRoute(c.getString("routeId"), c.getString("baseId"), c.getString("parkingId"),
                    c.getString("runwayId"), runwayBId, readStrings(c, "waypoints"), arrivalWaypoints, parkingHeading,
                    arrivalRunwayId, c.hasUUID("owner") ? c.getUUID("owner") : null);
            store.routes.put(route.routeId(), route);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TaxiRoute r : this.routes.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("routeId", r.routeId());
            c.putString("baseId", r.baseId());
            c.putString("parkingId", r.parkingId());
            c.putString("runwayId", r.runwayId());
            c.putString("runwayBId", r.runwayBId());
            c.putInt("parkingHeading", r.parkingHeading());
            c.put("waypoints", writeStrings(r.waypointIds()));
            c.put("arrivalWaypoints", writeStrings(r.arrivalWaypointIds()));
            if (!r.arrivalRunwayId().isEmpty()) {
                c.putString("arrivalRunwayId", r.arrivalRunwayId());
            }
            if (r.owner() != null) {
                c.putUUID("owner", r.owner());
            }
            list.add(c);
        }
        tag.put("routes", list);
        return tag;
    }

    private static List<String> readStrings(CompoundTag c, String key) {
        List<String> out = new ArrayList<>();
        ListTag list = c.getList(key, Tag.TAG_STRING);
        for (int j = 0; j < list.size(); j++) {
            out.add(list.getString(j));
        }
        return out;
    }

    private static ListTag writeStrings(List<String> values) {
        ListTag list = new ListTag();
        for (String s : values) {
            list.add(StringTag.valueOf(s));
        }
        return list;
    }
}
