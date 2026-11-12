package com.ivomens.mcheli.vehicle.world;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * The aircraft of one level: which are there, and which entity-storage sections hold them, bucketed by
 * chunk column.
 * <p>
 * Every entity move asks the level for the aircraft near it, so their part boxes can block the move.
 * That entity search walks the level's entity sections and is the expensive part, so it is skipped
 * when it could not find any aircraft at all, which is what {@link #mayFindAircraft} answers: whether
 * any aircraft sits in a section an entity search of an area would look at. For most entities in a
 * world that is a lookup in an empty map or a few empty buckets. The search has no side effects (hit
 * boxes are worked out by the damage code when a hit happens), so skipping it changes nothing else.
 * <p>
 * The sensors walk a level's aircraft through the same index ({@link #aircraft}) instead of a static set.
 * <p>
 * {@code MixinEntity} keeps it current: an aircraft is added when it enters the level's entity storage,
 * follows its section as it moves (the storage keys entities by the section of their block position,
 * and so does this index), and is dropped when it leaves the storage.
 */
public final class AircraftIndex {

    /** Implemented on {@link Level} by a mixin, so every level owns its own index. */
    public interface Holder {
        AircraftIndex mcheli$aircraftIndex();
    }

    /**
     * How far beyond a search area the entity storage looks for sections, horizontally and below the
     * area. The storage uses 2 and 4 blocks; one margin that covers both keeps this a safe superset.
     */
    private static final double SECTION_SEARCH_MARGIN = 4.0;
    private static final Predicate<AircraftEntity> ANY = aircraft -> true;

    private final Reference2LongMap<AircraftEntity> sections = new Reference2LongLinkedOpenHashMap<>();
    private final Long2ObjectMap<List<AircraftEntity>> columns = new Long2ObjectOpenHashMap<>();

    public static AircraftIndex of(Level level) {
        return ((Holder) level).mcheli$aircraftIndex();
    }

    /** The aircraft in the level, in the order they entered it. */
    public Collection<AircraftEntity> aircraft() {
        return this.sections.keySet();
    }

    /** How many aircraft stand in the chunk column, at any height. */
    public int countInChunk(int chunkX, int chunkZ) {
        List<AircraftEntity> bucket = this.columns.get(ChunkPos.asLong(chunkX, chunkZ));
        return bucket != null ? bucket.size() : 0;
    }

    public void add(AircraftEntity aircraft) {
        remove(aircraft);
        long section = SectionPos.asLong(aircraft.blockPosition());
        this.sections.put(aircraft, section);
        this.columns.computeIfAbsent(column(section), key -> new ArrayList<>(2)).add(aircraft);
    }

    public void remove(AircraftEntity aircraft) {
        if (this.sections.containsKey(aircraft)) {
            unlink(aircraft, this.sections.removeLong(aircraft));
        }
    }

    /** Follows an indexed aircraft to the section of its new position; others are ignored. */
    public void move(AircraftEntity aircraft) {
        if (!this.sections.containsKey(aircraft)) {
            return;
        }
        long previous = this.sections.getLong(aircraft);
        long current = SectionPos.asLong(aircraft.blockPosition());
        if (previous == current) {
            return;
        }
        this.sections.put(aircraft, current);
        if (column(previous) != column(current)) {
            unlink(aircraft, previous);
            this.columns.computeIfAbsent(column(current), key -> new ArrayList<>(2)).add(aircraft);
        }
    }

    /**
     * Whether an entity search of {@code box} grown by {@code reach} on every side could visit an
     * aircraft. False means {@code level.getEntitiesOfClass(AircraftEntity.class, box.inflate(reach))}
     * finds nothing and touches nothing. Takes the box and reach separately so the common answer,
     * false, needs no allocation.
     */
    public boolean mayFindAircraft(AABB box, double reach) {
        return mayFindAircraft(box, reach, ANY);
    }

    /**
     * Whether an entity search of {@code box} grown by {@code reach} could visit an aircraft that passes
     * {@code which}. False means such a search finds none of them; true means it may.
     */
    public boolean mayFindAircraft(AABB box, double reach, Predicate<AircraftEntity> which) {
        if (this.sections.isEmpty()) {
            return false;
        }
        double margin = reach + SECTION_SEARCH_MARGIN;
        int minX = SectionPos.posToSectionCoord(box.minX - margin);
        int minY = SectionPos.posToSectionCoord(box.minY - margin);
        int minZ = SectionPos.posToSectionCoord(box.minZ - margin);
        int maxX = SectionPos.posToSectionCoord(box.maxX + margin);
        int maxY = SectionPos.posToSectionCoord(box.maxY + margin);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + margin);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                List<AircraftEntity> bucket = this.columns.get(ChunkPos.asLong(x, z));
                if (bucket != null && anyBetween(bucket, minY, maxY, which)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean anyBetween(List<AircraftEntity> bucket, int minY, int maxY, Predicate<AircraftEntity> which) {
        for (int i = 0; i < bucket.size(); i++) {
            AircraftEntity aircraft = bucket.get(i);
            int y = SectionPos.y(this.sections.getLong(aircraft));
            if (y >= minY && y <= maxY && which.test(aircraft)) {
                return true;
            }
        }
        return false;
    }

    private void unlink(AircraftEntity aircraft, long section) {
        long key = column(section);
        List<AircraftEntity> bucket = this.columns.get(key);
        if (bucket == null) {
            return;
        }
        // by identity: Entity.equals compares ids, and the index must not drop a different entity
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i) == aircraft) {
                bucket.remove(i);
                break;
            }
        }
        if (bucket.isEmpty()) {
            this.columns.remove(key);
        }
    }

    private static long column(long section) {
        return ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
    }
}
