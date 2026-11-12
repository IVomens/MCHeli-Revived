package com.ivomens.mcheli.vehicle.sensor.client;

import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatTable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side tracker for RWR threats.
 */
public final class RWRThreatClientTracker {
    private static final ConcurrentHashMap<Integer, RWRThreatTable> TABLES = new ConcurrentHashMap<>();

    private RWRThreatClientTracker() {
    }

    public static void updateTable(RWRThreatTable incoming) {
        if (incoming == null) {
            return;
        }
        RWRThreatTable prev = TABLES.get(incoming.receiverEntityId());
        if (prev != null && prev.snapshotSeq() >= incoming.snapshotSeq()) {
            return;
        }
        TABLES.put(incoming.receiverEntityId(), incoming);
    }

    public static RWRThreatTable getTable(int receiverEntityId) {
        return TABLES.get(receiverEntityId);
    }

    public static List<RWRThreatEvent> getEvents(int receiverEntityId) {
        RWRThreatTable table = TABLES.get(receiverEntityId);
        if (table == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(table.events());
    }

    /** Drops every cached table (e.g. when leaving a world). */
    public static void clear() {
        TABLES.clear();
    }
}
