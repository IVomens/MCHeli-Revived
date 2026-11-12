package com.ivomens.mcheli.vehicle.sensor;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class for the RWR threat table sent to a receiving aircraft's crew.
 */
public class RWRThreatTable {
    private final int receiverEntityId;
    private final long snapshotSeq;
    private final List<RWRThreatEvent> events;

    public List<RWRThreatEvent> events() {
        return this.events;
    }

    public int receiverEntityId() {
        return this.receiverEntityId;
    }

    public long snapshotSeq() {
        return this.snapshotSeq;
    }

    public RWRThreatTable(int receiverEntityId, long snapshotSeq, List<RWRThreatEvent> events) {
        this.receiverEntityId = receiverEntityId;
        this.snapshotSeq = snapshotSeq;
        this.events = events != null ? events : new ArrayList<>();
    }
}
