package com.ivomens.mcheli.wingman.handler;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.ChunkTickets;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a small square of chunks loaded around UAVs that would otherwise freeze because no player is
 * near them.
 *
 * <p>A UAV gets a lease only when ALL of these hold:
 * <ul>
 *   <li>chunk loading is enabled ({@code EnableUavChunkLoading} or the wingman
 *       {@code forceChunkload} option; both default to off);</li>
 *   <li>it is actively flown from a UAV station by a player, or it is a pilotless aircraft flying an
 *       autonomous wingman mission (manned aircraft are always kept loaded by their pilot);</li>
 *   <li>it is outside every player's simulation area.</li>
 * </ul>
 *
 * <p>Chunks are forced through a {@link ChunkTickets} set keyed by the aircraft UUID, with
 * non-ticking tickets (entities in a forced chunk tick either way). The square has radius
 * {@code UavChunkLoadRadius}; it is only recomputed when the UAV crosses a chunk boundary,
 * and chunks are released only once they are more than one chunk outside that radius (hysteresis), so a
 * UAV flying along a chunk border does not churn tickets. New chunks are only forced once they are
 * already loaded (the neighbours of a forced chunk always are), so ticket handling never triggers a
 * synchronous chunk load on the server thread. Leases are dropped on death/removal, when the operator
 * disconnects or stops flying, and tickets are never restored after a restart.
 */
public final class ChunkLoadHandler {

    public static final ChunkTickets.TicketSet TICKETS = Services.CHUNK_TICKETS.create(
            ResourceLocation.fromNamespaceAndPath(McHeliWingman.MODID, "uav_chunks"));

    /** Ticks between re-evaluations of a UAV that has not changed chunk (players move too). */
    private static final int RECHECK_INTERVAL = 20;
    /** Faster retry while some wanted chunks were not loaded yet. */
    private static final int PENDING_RETRY_INTERVAL = 5;
    private static final int MAX_RADIUS = 8;

    /** Per-aircraft evaluation schedule, kept for aircraft without a lease too. */
    private static final class Schedule {
        long nextCheckTick;
        long chunk;
    }

    private static final class Lease {
        final UUID owner;
        final ServerLevel level;
        final LongOpenHashSet forced = new LongOpenHashSet();
        int centerX, centerZ;
        int radius = -1;

        Lease(UUID owner, ServerLevel level) {
            this.owner = owner;
            this.level = level;
        }
    }

    private final Roster roster;
    /** Active leases by aircraft UUID. */
    private final Map<UUID, Lease> leases = new HashMap<>();
    private final Map<UUID, Schedule> schedules = new HashMap<>();

    public ChunkLoadHandler(Roster roster) {
        this.roster = roster;
    }

    /** Chunks the UAV and wingman leases currently keep loaded in the level. */
    public int forcedChunks(Level level) {
        int count = 0;
        for (Lease lease : this.leases.values()) {
            if (lease.level == level) {
                count += lease.forced.size();
            }
        }
        return count;
    }

    public static boolean isEnabled() {
        return McheliConfig.uav().chunkLoading() || WingmanConfig.get().forceChunkload();
    }

    public void onLevelTick(Level tickingLevel) {
        if (!(tickingLevel instanceof ServerLevel level)) return;

        if (!isEnabled()) {
            if (!leases.isEmpty()) releaseAll(level);
            schedules.clear();
            return;
        }

        long now = level.getGameTime();
        int radius = Math.max(0, Math.min(MAX_RADIUS, McheliConfig.uav().chunkLoadRadius()));

        for (AircraftEntity ac : new ArrayList<>(AircraftIndex.of(level).aircraft())) {
            UUID id = ac.getUUID();
            int cx = SectionPos.blockToSectionCoord(ac.getX());
            int cz = SectionPos.blockToSectionCoord(ac.getZ());

            long chunk = ChunkPos.asLong(cx, cz);
            Schedule schedule = schedules.get(id);
            boolean moved = schedule == null || schedule.chunk != chunk;
            if (!moved && now < schedule.nextCheckTick) continue;
            if (schedule == null) {
                schedule = new Schedule();
                schedules.put(id, schedule);
            }
            schedule.chunk = chunk;
            schedule.nextCheckTick = now + RECHECK_INTERVAL;

            Lease lease = leases.get(id);
            boolean wanted = !ac.isRemoved() && needsLoading(ac)
                    && !insidePlayerArea(level, cx, cz, lease != null);
            if (!wanted) {
                if (lease != null) release(lease);
                continue;
            }
            if (lease == null) {
                lease = new Lease(id, level);
                leases.put(id, lease);
                McHeliWingman.logger.debug("[ChunkLoad] lease acquired for {} at chunk ({},{})", id, cx, cz);
            }
            if (update(lease, cx, cz, radius)) {
                schedule.nextCheckTick = now + PENDING_RETRY_INTERVAL;
            }
        }
    }

    /**
     * True for a UAV that a player is flying from a station, or a pilotless aircraft flying an
     * autonomous wingman mission. Manned aircraft are loaded by their pilot and never qualify.
     */
    private boolean needsLoading(AircraftEntity ac) {
        if (ac.getRiddenByEntity() instanceof Player) return false;
        if (ac.isUAV()) {
            IUavStation station = ac.uavLink().station();
            Entity operator = station != null ? station.getOperator() : null;
            if (operator instanceof ServerPlayer p && p.isAlive() && !p.hasDisconnected()) return true;
        }
        Wingman wingman = this.roster.get(ac.getUUID());
        return wingman != null && wingman.isAutonomous();
    }

    /**
     * True if some player keeps the UAV's chunk (and its immediate surroundings) simulated. Uses a
     * one-chunk hysteresis: an existing lease is only dropped once the UAV is clearly inside a player's
     * area, so it is not acquired/released repeatedly at the edge.
     */
    private static boolean insidePlayerArea(ServerLevel level, int cx, int cz, boolean hasLease) {
        int sim = level.getServer().getPlayerList().getSimulationDistance();
        int limit = Math.max(0, sim - (hasLease ? 3 : 2));
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int px = SectionPos.blockToSectionCoord(p.getX());
            int pz = SectionPos.blockToSectionCoord(p.getZ());
            if (Math.max(Math.abs(px - cx), Math.abs(pz - cz)) <= limit) return true;
        }
        return false;
    }

    /**
     * Moves the lease to the given centre. Releases chunks farther than {@code radius + 1}, forces
     * missing chunks within {@code radius} that are already loaded.
     *
     * @return true if some wanted chunks were skipped because they are not loaded yet
     */
    private static boolean update(Lease lease, int cx, int cz, int radius) {
        boolean sameCenter = lease.centerX == cx && lease.centerZ == cz && lease.radius == radius;
        lease.centerX = cx;
        lease.centerZ = cz;
        lease.radius = radius;

        if (!sameCenter) {
            int keep = radius + 1;
            LongIterator it = lease.forced.iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int x = ChunkPos.getX(key);
                int z = ChunkPos.getZ(key);
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) > keep) {
                    TICKETS.release(lease.level, lease.owner, x, z, false);
                    it.remove();
                }
            }
        }

        boolean pending = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                long key = ChunkPos.asLong(x, z);
                if (lease.forced.contains(key)) continue;
                // Only force chunks that are already loaded: forcing a chunk fetches it synchronously,
                // which must never turn into a blocking load/generation.
                if (lease.level.getChunkSource().getChunkNow(x, z) == null) {
                    pending = true;
                    continue;
                }
                TICKETS.force(lease.level, lease.owner, x, z, false);
                lease.forced.add(key);
            }
        }
        return pending;
    }

    private void release(Lease lease) {
        leases.remove(lease.owner);
        LongIterator it = lease.forced.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            TICKETS.release(lease.level, lease.owner, ChunkPos.getX(key), ChunkPos.getZ(key), false);
        }
        lease.forced.clear();
        McHeliWingman.logger.debug("[ChunkLoad] lease released for {}", lease.owner);
    }

    private void releaseAll(ServerLevel level) {
        for (Lease lease : new ArrayList<>(leases.values())) {
            if (lease.level == level) release(lease);
        }
    }

    /** Death, removal or unload of a leased aircraft releases its chunks immediately. */
    public void onEntityLeave(Entity entity, Level level) {
        if (!(level instanceof ServerLevel)) return;
        if (!(entity instanceof AircraftEntity ac)) return;
        UUID id = ac.getUUID();
        schedules.remove(id);
        Lease lease = leases.get(id);
        if (lease != null && lease.level == level) release(lease);
    }

    /** An operator disconnecting: re-evaluate every lease on the next tick. */
    public void onPlayerLogout() {
        if (leases.isEmpty()) return;
        for (Lease lease : leases.values()) {
            Schedule schedule = schedules.get(lease.owner);
            if (schedule != null) schedule.nextCheckTick = 0;
        }
    }

    /** The stopped server's levels took their tickets with them; drop the bookkeeping. */
    public void clear() {
        leases.clear();
        schedules.clear();
    }

    /**
     * An unloading level discards its own tickets; just drop our bookkeeping for it. Anything left in
     * the saved forced-chunk data is dropped on the next load (see {@link ChunkTickets}).
     */
    public void onLevelUnload(LevelAccessor unloaded) {
        if (!(unloaded instanceof ServerLevel level)) return;
        leases.values().removeIf(l -> l.level == level);
        if (leases.isEmpty()) schedules.clear();
    }
}
