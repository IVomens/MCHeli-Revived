package com.ivomens.mcheli.api.vehicle.action;

/**
 * Whether a {@link VehicleSpawner} placed its vehicle, and if not, why.
 *
 * <p><b>Stability.</b> Since API 1.1. A minor version may add constants, so a {@code switch} over them needs a
 * {@code default} branch.
 */
public enum SpawnStatus {
    /** The vehicle is in the level. */
    SPAWNED,
    /** No vehicle type of that kind and id is loaded. */
    UNKNOWN_TYPE,
    /** The position is in a chunk that is not loaded; MCHeli never loads chunks for a spawn. */
    CHUNK_NOT_LOADED,
    /** Blocks or entities are in the way, and the spawner was asked to require free space. */
    OBSTRUCTED,
    /** A vehicle limit of the server's admin config is reached, and the spawner was asked to respect them. */
    LIMIT_REACHED,
    /** A rider was given for a seat the vehicle type does not have. */
    INVALID_SEAT,
    /** The level refused the entity (another mod cancelled it joining the level). */
    REFUSED;

    /** Whether the vehicle is in the level. */
    public boolean isSpawned() {
        return this == SPAWNED;
    }
}
