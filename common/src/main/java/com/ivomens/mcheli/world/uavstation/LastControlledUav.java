package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The UAV a station controlled last, so its operator can take it over again. After a load the station
 * only knows the UAV's id, and searches for it with a growing pause between tries, so a station whose
 * UAV is gone for good stops scanning.
 */
final class LastControlledUav {

    private static final int MAX_SEARCH_INTERVAL = 600;
    private static final int INITIAL_SEARCH_INTERVAL = 40;

    private final UavStationEntity station;
    /** The synced entity id of the UAV, for the station's client. */
    private final EntityDataAccessor<Integer> entityId;
    @Nullable
    private AircraftEntity aircraft;
    /** The id the station saved; empty once the UAV was found again. */
    private String loadedGuid = "";
    private int searchInterval = INITIAL_SEARCH_INTERVAL;
    private int nextSearchTick;

    LastControlledUav(UavStationEntity station, EntityDataAccessor<Integer> entityId) {
        this.station = station;
        this.entityId = entityId;
    }

    @Nullable
    AircraftEntity get() {
        return this.aircraft;
    }

    void set(@Nullable AircraftEntity aircraft) {
        this.aircraft = aircraft;
    }

    /** The UAV, looked up by its synced entity id when this side does not know it yet. */
    @Nullable
    AircraftEntity getOrFind() {
        if (this.aircraft == null) {
            int id = this.entityId();
            if (id > 0 && this.station.level().getEntity(id) instanceof AircraftEntity ac && ac.isUAV()) {
                this.aircraft = ac;
            }
        }
        return this.aircraft;
    }

    int entityId() {
        return this.station.getEntityData().get(this.entityId);
    }

    void setEntityId(int id) {
        if (!this.station.level().isClientSide) {
            this.station.getEntityData().set(this.entityId, id);
        }
    }

    /** Forgets a UAV that was removed. */
    void dropRemoved() {
        if (this.aircraft instanceof AircraftEntity ac && ac.isRemoved()) {
            this.aircraft = null;
        }
    }

    /** Server: searches for the saved UAV, backing off between failed searches. */
    void tickSearch() {
        if (this.aircraft == null && !this.loadedGuid.isEmpty() && this.station.tickCount >= this.nextSearchTick) {
            this.search();
            if (!this.loadedGuid.isEmpty()) {
                this.searchInterval = Math.min(this.searchInterval * 2, MAX_SEARCH_INTERVAL);
            } else {
                this.searchInterval = INITIAL_SEARCH_INTERVAL;
            }
            this.nextSearchTick = this.station.tickCount + this.searchInterval;
        }
    }

    /** Looks for the saved UAV within the configured UAV range, so a far-flung one is still found. */
    private void search() {
        double range = WingmanConfig.get().uavSearchRange();
        String guid = this.loadedGuid;
        List<AircraftEntity> found = this.station.level().getEntitiesOfClass(AircraftEntity.class,
                this.station.getBoundingBox().inflate(range, range, range), ac -> guid.equals(ac.identity().uniqueId()));
        if (!found.isEmpty()) {
            AircraftEntity ac = found.getFirst();
            this.aircraft = ac;
            this.setEntityId(EntityIds.of(ac));
            this.loadedGuid = "";
        }
    }

    void save(CompoundTag nbt) {
        String guid = this.aircraft instanceof AircraftEntity ac && !ac.isRemoved()
                ? ac.identity().uniqueId()
                : this.loadedGuid;
        nbt.putString("LastCtrlAc", guid);
    }

    void load(CompoundTag nbt) {
        this.loadedGuid = nbt.getString("LastCtrlAc");
    }

    /** Takes the UAV over again, if it is still there. */
    @Nullable
    AircraftEntity reconnectable() {
        return this.aircraft != null && !this.aircraft.isRemoved() ? this.aircraft : null;
    }
}
