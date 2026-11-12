package com.ivomens.mcheli.config;

/**
 * How often and how far the server sends things to clients, keeping projectiles loaded, and how long a
 * projectile waits in chunks that tick no entities. Server side.
 */
public record NetworkSettings(int radarUpdateInterval, double entityTrackingRangeMultiplier,
                              boolean projectileChunkLoad, int projectileChunkLeaseTicks,
                              int frozenProjectileLifetime) {

    public static final IntOption RADAR_UPDATE_INTERVAL = IntOption.of("RadarUpdateInterval", 5).range(1, 200)
            .describe("RadarUpdateInterval = server ticks between radar contact updates sent to players (20 ticks = 1"
                    + " second). Higher values reduce server load and network traffic.");
    public static final DoubleOption ENTITY_TRACKING_RANGE_MULTIPLIER = DoubleOption
            .of("EntityTrackingRangeMultiplier", 1.0).range(0.1, 10.0)
            .describe("EntityTrackingRangeMultiplier = multiplier for the distance at which MCHeli vehicles and"
                    + " projectiles are sent to clients (always capped by the server view distance).");
    public static final BoolOption PROJECTILE_CHUNK_LOAD = BoolOption.of("ProjectileChunkLoad", false)
            .describe("ProjectileChunkLoad = force-load chunks along the flight path of EVERY projectile so"
                    + " bullets/rockets/missiles keep simulating and can hit targets in terrain no player is loading."
                    + " false (default) = only weapons whose definition sets CanLoadChunks force chunks. Chunks are"
                    + " leased (see ProjectileChunkLeaseTicks) and shared between projectiles. Can increase server"
                    + " load when many projectiles are in flight.");
    public static final IntOption PROJECTILE_CHUNK_LEASE_TICKS = IntOption.of("ProjectileChunkLeaseTicks", 40)
            .describe("ProjectileChunkLeaseTicks = extra ticks a chunk stays force-loaded after the last projectile"
                    + " left it, bridging the gap between consecutive shots in a burst so the same chunks are not"
                    + " unloaded and reloaded repeatedly (20 ticks = 1 second).");
    public static final IntOption FROZEN_PROJECTILE_LIFETIME = IntOption.of("FrozenProjectileLifetime", 30)
            .range(0, 86400)
            .describe("FrozenProjectileLifetime = seconds of game time a projectile (bullet, rocket, missile, bomb,"
                    + " torpedo) may wait in a chunk that is loaded but ticks no entities (no player near) before"
                    + " it is removed without exploding. Shorter waits are unaffected: the projectile flies on when"
                    + " the chunk ticks again. 0 = never remove them. Laid mines are never removed. Projectiles"
                    + " other than mines are not saved with the world either.");

    static NetworkSettings from(ConfigValues v) {
        return new NetworkSettings(v.get(RADAR_UPDATE_INTERVAL), v.get(ENTITY_TRACKING_RANGE_MULTIPLIER),
                v.get(PROJECTILE_CHUNK_LOAD), v.get(PROJECTILE_CHUNK_LEASE_TICKS), v.get(FROZEN_PROJECTILE_LIFETIME));
    }
}
