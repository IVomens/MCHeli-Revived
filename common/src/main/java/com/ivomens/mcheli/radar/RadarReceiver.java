package com.ivomens.mcheli.radar;

import com.ivomens.mcheli.combat.weapon.net.RadarContact;
import com.ivomens.mcheli.combat.weapon.net.RadarContactFix;
import com.ivomens.mcheli.combat.weapon.net.RadarContactsMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The radar picture one player's client holds, so that each snapshot only carries what changed: the
 * contacts the client has not been told about (or that changed what they are), the ones that moved,
 * and the ids of the ones that stayed put. Nothing is sent while nothing changed, except a heartbeat
 * every {@link #HEARTBEAT_MS} that keeps the client's contacts from expiring (they expire after
 * {@code EntityInfoClientTracker.EXPIRATION_MS}), and a full picture every {@link #KEYFRAME_MS} in case
 * the client lost one anyway.
 * <p>
 * Snapshots are built on virtual threads; this object takes them one at a time and drops one older
 * than a snapshot it already sent, so the client applies every change in order.
 */
final class RadarReceiver {

    /** Longest silence while nothing changes; well below the client's five seconds of expiry. */
    static final long HEARTBEAT_MS = 1_000L;
    /** How often everything is sent again in full. */
    static final long KEYFRAME_MS = 10_000L;
    /** Fixed point used to tell whether a contact moved: what {@link RadarContactFix} resolves. */
    private static final double UNITS = RadarContactFix.UNITS_PER_BLOCK;

    private final ServerPlayer player;
    /** What the client was told about each contact of the last snapshot, by entity id. */
    private Map<Integer, EntityInfo> known = new HashMap<>();
    @Nullable
    private String dimension;
    private long lastSeq = -1L;
    private long lastSentMs = Long.MIN_VALUE / 2;
    private long lastKeyframeMs = Long.MIN_VALUE / 2;

    RadarReceiver(ServerPlayer player) {
        this.player = player;
    }

    ServerPlayer player() {
        return this.player;
    }

    /**
     * The message that brings the client from the last snapshot to this one, or null when there is
     * nothing to send.
     *
     * @param visible the contacts in range, sorted by entity id
     * @param origin  where the receiver stands
     */
    @Nullable
    synchronized RadarContactsMessage next(long seq, String dimension, Vec3 origin, List<EntityInfo> visible,
                                           long nowMs) {
        if (seq <= this.lastSeq) {
            return null;
        }
        this.lastSeq = seq;
        boolean keyframe = nowMs - this.lastKeyframeMs >= KEYFRAME_MS || !dimension.equals(this.dimension);
        if (keyframe) {
            // contact ids are only unique within a level
            this.known.clear();
            this.dimension = dimension;
            this.lastKeyframeMs = nowMs;
        }
        if (visible.isEmpty()) {
            this.known.clear();
            return null;
        }
        int originX = (int) Math.floor(origin.x);
        int originY = (int) Math.floor(origin.y);
        int originZ = (int) Math.floor(origin.z);
        List<RadarContact> introduced = new ArrayList<>();
        List<RadarContactFix> fixes = new ArrayList<>();
        int[] unchanged = new int[visible.size()];
        int unchangedCount = 0;
        Map<Integer, EntityInfo> next = new HashMap<>(visible.size() * 2);
        for (EntityInfo info : visible) {
            EntityInfo before = this.known.get(info.entityId());
            if (before == null || !sameIdentity(before, info)) {
                introduced.add(new RadarContact(info.entityId(), RadarContact.shortName(info.worldName()),
                        RadarContact.shortName(info.entityName()), info.kind()));
                fixes.add(fix(info, originX, originY, originZ));
            } else if (!samePosition(before, info)) {
                fixes.add(fix(info, originX, originY, originZ));
            } else {
                unchanged[unchangedCount++] = info.entityId();
            }
            next.put(info.entityId(), info);
        }
        boolean changed = !introduced.isEmpty() || !fixes.isEmpty() || next.size() != this.known.size();
        this.known = next;
        if (!changed && nowMs - this.lastSentMs < HEARTBEAT_MS) {
            return null;
        }
        this.lastSentMs = nowMs;
        return new RadarContactsMessage(seq, originX, originY, originZ, introduced, fixes,
                java.util.Arrays.copyOf(unchanged, unchangedCount));
    }

    private static RadarContactFix fix(EntityInfo info, int originX, int originY, int originZ) {
        return RadarContactFix.of(info.entityId(), originX, originY, originZ, new Vec3(info.x(), info.y(), info.z()),
                new Vec3(info.prevX(), info.prevY(), info.prevZ()));
    }

    private static boolean sameIdentity(EntityInfo a, EntityInfo b) {
        return a.worldName().equals(b.worldName()) && a.entityName().equals(b.entityName())
                && a.kind() == b.kind();
    }

    /** Equal to the resolution the client gets the positions in. */
    private static boolean samePosition(EntityInfo a, EntityInfo b) {
        return units(a.x()) == units(b.x()) && units(a.y()) == units(b.y()) && units(a.z()) == units(b.z())
                && units(a.prevX()) == units(b.prevX()) && units(a.prevY()) == units(b.prevY())
                && units(a.prevZ()) == units(b.prevZ());
    }

    private static long units(double blocks) {
        return Math.round(blocks * UNITS);
    }
}
