package com.ivomens.mcheli.radar.client;

import com.ivomens.mcheli.radar.EntityInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of the server's radar contacts. The server only sends full snapshots, so contacts are never
 * removed explicitly: they expire when they stop showing up.
 * <ul>
 *     <li>Snapshots older than the last applied one are ignored (packets can arrive out of order).</li>
 *     <li>A contact expires after {@link #EXPIRATION_MS} or after {@link #MISSING_SEQ_THRESHOLD} snapshots
 *     without it.</li>
 *     <li>Cleanup runs on the client tick, so no extra thread is involved.</li>
 * </ul>
 *
 * <p>One per client, owned by the client session.
 *
 * @author TV90
 */
public final class EntityInfoClientTracker {

    private final Map<Integer, Tracked> tracked = new ConcurrentHashMap<>();
    /**
     * Timeout threshold in milliseconds for missing heartbeats (e.g., 5s)
     */
    public static final long EXPIRATION_MS = 5_000L;
    /**
     * Missing heartbeat threshold in sequence numbers (based on server ticks, at 20 TPS, 100 ≈ 5s)
     */
    public static final long MISSING_SEQ_THRESHOLD = 100L;
    /**
     * Tick interval for cleanup scans (e.g., scan every 10 client ticks)
     */
    public static final int CLEANUP_TICK_INTERVAL = 10;
    private volatile long lastAppliedSeq = -1L;
    /** Highest snapshot sequence received, used to detect contacts that stopped appearing. */
    private volatile long latestSeqObserved = -1L;
    private int clientTickCounter = 0;

    /**
     * Called by network packet callback: applies a batch of entities and records the snapshot sequence
     */
    public void updateEntities(List<EntityInfo> infos, long snapshotSeq) {
        // Protect against out-of-order or delayed packets
        if (snapshotSeq < lastAppliedSeq) {
            return;
        }

        long now = System.currentTimeMillis();

        for (EntityInfo info : infos) {
            Tracked t = tracked.get(info.entityId());
            if (t == null) {
                tracked.put(info.entityId(), new Tracked(info, now, snapshotSeq));
            } else {
                t.info = info;
                t.lastSeenMillis = now;
                t.lastSeenSeq = snapshotSeq;
            }
        }

        latestSeqObserved = Math.max(latestSeqObserved, snapshotSeq);
        lastAppliedSeq = snapshotSeq;
    }

    public EntityInfo getEntityInfo(int entityId) {
        Tracked t = tracked.get(entityId);
        return t == null ? null : t.info;
    }

    public Collection<EntityInfo> getAllTrackedEntities() {
        List<EntityInfo> out = new ArrayList<>(tracked.size());
        for (Tracked t : tracked.values()) {
            out.add(t.info);
        }
        return Collections.unmodifiableCollection(out);
    }

    /**
     * Periodic cleanup: remove entries that have been missing too long (by time or sequence)
     */
    private void cleanupExpired() {
        if (tracked.isEmpty()) return;

        long now = System.currentTimeMillis();
        long seqNow = latestSeqObserved;

        Iterator<Map.Entry<Integer, Tracked>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Tracked> e = it.next();
            Tracked t = e.getValue();

            boolean timeExpired = (now - t.lastSeenMillis) > EXPIRATION_MS;
            boolean seqExpired = (seqNow - t.lastSeenSeq) > MISSING_SEQ_THRESHOLD;

            if (timeExpired || seqExpired) {
                it.remove();
            }
        }
    }

    public static final class Tracked {

        volatile EntityInfo info;
        volatile long lastSeenMillis;
        volatile long lastSeenSeq;

        Tracked(EntityInfo info, long now, long seq) {
            this.info = info;
            this.lastSeenMillis = now;
            this.lastSeenSeq = seq;
        }
    }

    /** Periodic cleanup, called at the end of every client tick. */
    public void onClientTick() {
        clientTickCounter++;
        if (clientTickCounter % CLEANUP_TICK_INTERVAL == 0) {
            cleanupExpired();
        }
    }
}
