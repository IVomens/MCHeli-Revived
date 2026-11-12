package com.ivomens.mcheli.gametest.bench;

import java.util.Map;

/**
 * The measurements of one tier: a workload with a number of vehicles. Tick times are milliseconds,
 * allocation bytes per tick, network bytes per tick. {@code netBytesPerVehicle} divides what the
 * observer was sent about MCHeli by the number of vehicles it tracked.
 * <p>
 * The network breakdown is per tick: {@code netByKind} and {@code netCountByKind} hold the bytes and
 * packets of every kind of packet the observer got, {@code netByGroup} and {@code netCountByGroup} the
 * same summed into the groups of {@link PacketMeter#group}, {@code netEntityData} the bytes of each
 * synced value in the entity data packets, {@code netByEntityType} the vanilla packets per entity type.
 * {@code pilotBytes} and {@code pilotByKind} are what one crew member (the first to board) was sent
 * about MCHeli; zero in tiers without crew. Runs recorded before the breakdown existed have nulls there.
 * {@code projectiles} counts the projectiles while the tier was measured (null in older runs).
 */
record TierResult(String workload, int vehicles, int spawned, int alive, int crewAboard, int gunners,
                  int wingmen, int entities, int ticks,
                  double msMean, double msP50, double msP95, double msP99, double msMax,
                  double allocMean, double allocP50,
                  double trackedVehicles, double netModBytes, double netVanillaBytes, double netBytesPerVehicle,
                  Map<String, Double> netByKind, Map<String, Double> netCountByKind,
                  Map<String, Double> netByGroup, Map<String, Double> netCountByGroup,
                  Map<String, Double> netEntityData, Map<String, Double> netByEntityType, double pilotBytes,
                  Map<String, Double> pilotByKind, Projectiles projectiles) {

    /**
     * Projectiles alive after a tick (mean), of those the ones in chunks that tick no entities (frozen), fired
     * or split off per tick, and the ticks flown by those that ended while the tier was measured.
     */
    record Projectiles(double alive, double frozen, double spawnedPerTick, double lifetimeTicks) {
    }

    String key() {
        return this.workload + "/" + this.vehicles;
    }

    /** Two runs of the same tier: the tick times of the faster one, the allocation of the leaner one. */
    TierResult bestOf(TierResult other) {
        TierResult time = other.msP50 < this.msP50 ? other : this;
        TierResult alloc = other.allocMean < this.allocMean ? other : this;
        return new TierResult(this.workload, this.vehicles, time.spawned, time.alive, time.crewAboard, time.gunners,
                time.wingmen, time.entities, time.ticks, time.msMean, time.msP50, time.msP95, time.msP99, time.msMax,
                alloc.allocMean, alloc.allocP50, time.trackedVehicles, time.netModBytes, time.netVanillaBytes,
                time.netBytesPerVehicle, time.netByKind, time.netCountByKind, time.netByGroup, time.netCountByGroup,
                time.netEntityData, time.netByEntityType, time.pilotBytes, time.pilotByKind, time.projectiles);
    }
}
