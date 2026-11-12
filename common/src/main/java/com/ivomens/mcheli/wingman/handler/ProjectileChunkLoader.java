package com.ivomens.mcheli.wingman.handler;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.ChunkTickets;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the chunks just ahead of an in-flight MCHeli projectile loaded so it keeps simulating (and can
 * hit targets) in terrain that no player is keeping loaded. Opt-in only: a projectile asks for a lease
 * when {@code ProjectileChunkLoad} is on or its weapon sets {@code CanLoadChunks}
 * ({@code ProjectileEntity#isChunkLoadingEnabled}); a player-steered TV missile additionally when
 * the wingman {@code tvmissile.chunkLoad} option is on.
 *
 * <p>Projectiles arrive in bursts that travel almost the same line, so the design is a shared,
 * <b>lease-based</b> one instead of a ticket per projectile:
 * <ul>
 *   <li>Each tick a projectile calls {@link #requestPath} for its own chunk plus the chunks it will
 *       cross in the next few ticks (at most {@link #MAX_LEAD_BLOCKS} ahead).</li>
 *   <li>Every requested chunk gets (or refreshes) a lease that expires
 *       {@code ProjectileChunkLeaseTicks} ticks later, so the gap between consecutive shots
 *       keeps the same chunks alive instead of unload/reload thrashing.</li>
 *   <li>Leases are chunk-keyed and shared, so many projectiles down the same corridor cost one forced
 *       chunk each, not one per projectile.</li>
 *   <li>Chunks a player already keeps entity-ticking are not leased at all, and chunks that are not
 *       loaded yet are skipped (never loaded synchronously from tick code).</li>
 *   <li>A sweep at the end of each level tick releases leases that have lapsed.</li>
 * </ul>
 * Tickets are non-ticking (loading only; entities in a forced chunk tick regardless) and are never
 * reinstated after a restart.
 */
public final class ProjectileChunkLoader {

    public static final ChunkTickets.TicketSet TICKETS = Services.CHUNK_TICKETS.create(
            ResourceLocation.fromNamespaceAndPath(McHeliWingman.MODID, "projectile_chunks"));

    /** All projectile leases share one ticket owner; the ticket set is keyed by chunk. */
    private static final UUID LEASE_OWNER = UUID.nameUUIDFromBytes("mcheli:projectile_chunk_lease".getBytes(StandardCharsets.UTF_8));

    /** Velocity-scaled lookahead: travel time (ticks) the leased line covers. */
    private static final int VELOCITY_LEAD_TICKS = 8;
    /** Minimum lookahead (blocks) beyond the projectile's own chunk. */
    private static final double MIN_LEAD_BLOCKS = 16.0;
    /** Cap (blocks) on the lookahead. */
    private static final double MAX_LEAD_BLOCKS = 32.0;
    /** Step (blocks) used to walk the path and collect the chunks it crosses. */
    private static final double SAMPLE_STEP = 8.0;

    /** Per-level lease bookkeeping: chunk -> game tick at which its lease lapses. */
    private final Map<Level, Long2LongOpenHashMap> leases = new IdentityHashMap<>();

    private final LongArrayList scratch = new LongArrayList();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

    /** Steered TV missiles in flight, per level, which the {@code tvmissile.chunkLoad} option leases for. */
    private final Map<Level, Set<ProjectileEntity>> tvMissiles = new IdentityHashMap<>();

    /** Chunks the projectile leases currently keep loaded in the level. */
    public int forcedChunks(Level level) {
        Long2LongOpenHashMap inLevel = this.leases.get(level);
        return inLevel != null ? inLevel.size() : 0;
    }

    public void onEntityJoin(Entity entity, Level level) {
        if (isTvMissile(entity) && level instanceof ServerLevel) {
            this.tvMissiles.computeIfAbsent(level, k -> new LinkedHashSet<>()).add((ProjectileEntity) entity);
        }
    }

    public void onEntityLeave(Entity entity, Level level) {
        Set<ProjectileEntity> inLevel = this.tvMissiles.get(level);
        if (inLevel != null && isTvMissile(entity)) {
            inLevel.remove(entity);
        }
    }

    private static boolean isTvMissile(Entity entity) {
        return entity instanceof ProjectileEntity projectile && projectile.kind() == ProjectileKind.TV_MISSILE;
    }

    /** Leases (or extends the lease of) the chunks along this projectile's path. No-op on the client. */
    public void requestPath(ProjectileEntity bullet) {
        if (!(bullet.level() instanceof ServerLevel level) || bullet.isRemoved()) return;

        Long2LongOpenHashMap expiry = leases.computeIfAbsent(level, k -> {
            Long2LongOpenHashMap m = new Long2LongOpenHashMap();
            m.defaultReturnValue(Long.MIN_VALUE);
            return m;
        });
        long until = level.getGameTime() + Math.max(0, McheliConfig.network().projectileChunkLeaseTicks());

        computePathChunks(bullet, scratch);
        for (int i = 0; i < scratch.size(); i++) {
            long key = scratch.getLong(i);
            long prev = expiry.get(key);
            if (prev != Long.MIN_VALUE) {
                if (until > prev) expiry.put(key, until);
                continue;
            }
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            // Never trigger a load from here; chunks next to a ticking chunk are loaded anyway.
            if (level.getChunkSource().getChunkNow(x, z) == null) continue;
            // Already simulated because a player is near: nothing to lease.
            scratchPos.set(SectionPos.sectionToBlockCoord(x, 8), bullet.getBlockY(), SectionPos.sectionToBlockCoord(z, 8));
            if (level.isPositionEntityTicking(scratchPos)) continue;
            TICKETS.force(level, LEASE_OWNER, x, z, false);
            expiry.put(key, until);
        }
    }

    /** Lease steered TV missiles (if enabled), then release leases that have lapsed. */
    public void onLevelTick(Level tickingLevel) {
        if (!(tickingLevel instanceof ServerLevel level)) return;

        if (WingmanConfig.get().tvMissileChunkLoad()) {
            for (ProjectileEntity tv : this.tvMissiles.getOrDefault(level, Set.of())) {
                if (tv.isRemoved()) continue;
                if (tv.shooter() instanceof ServerPlayer p && p.isAlive() && p.level() == level) {
                    requestPath(tv);
                }
            }
        }

        Long2LongOpenHashMap expiry = leases.get(level);
        if (expiry == null || expiry.isEmpty()) return;

        long now = level.getGameTime();
        ObjectIterator<Long2LongMap.Entry> it = expiry.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            // Keep while the lease is still current (expiry >= now): a chunk requested THIS tick with a
            // lease of 0 survives the tick it was requested in.
            if (e.getLongValue() >= now) continue;
            long key = e.getLongKey();
            it.remove();
            TICKETS.release(level, LEASE_OWNER, ChunkPos.getX(key), ChunkPos.getZ(key), false);
        }
    }

    /** The stopped server's levels took their tickets with them; drop the bookkeeping. */
    public void clear() {
        leases.clear();
        tvMissiles.clear();
    }

    /** An unloading level tears down its own tickets; drop our bookkeeping for it. */
    public void onLevelUnload(LevelAccessor unloaded) {
        if (unloaded instanceof Level level) {
            leases.remove(level);
            tvMissiles.remove(level);
        }
    }

    /**
     * Chunks to lease for a projectile: its current chunk plus the chunks ahead of it along its
     * horizontal motion, covering {@link #VELOCITY_LEAD_TICKS} ticks of travel (16 to 32 blocks).
     */
    private static void computePathChunks(ProjectileEntity b, LongArrayList out) {
        out.clear();
        double px = b.getX();
        double pz = b.getZ();
        long own = ChunkPos.asLong(SectionPos.blockToSectionCoord(px), SectionPos.blockToSectionCoord(pz));
        out.add(own);

        Vec3 m = b.getDeltaMovement();
        double speed = Math.sqrt(m.x * m.x + m.z * m.z);
        if (speed < 1.0e-4) {
            // Stationary or vertical: holding the current chunk is enough.
            return;
        }

        double lead = Math.min(MAX_LEAD_BLOCKS, Math.max(MIN_LEAD_BLOCKS, speed * VELOCITY_LEAD_TICKS));
        double ux = m.x / speed;
        double uz = m.z / speed;
        long last = own;
        for (double d = SAMPLE_STEP; d <= lead; d += SAMPLE_STEP) {
            long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(px + ux * d),
                                       SectionPos.blockToSectionCoord(pz + uz * d));
            if (key != last && !out.contains(key)) out.add(key);
            last = key;
        }
    }
}
