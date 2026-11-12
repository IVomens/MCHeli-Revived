package com.ivomens.mcheli.wingman;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.wingman.control.WingmanTicker;
import com.ivomens.mcheli.wingman.handler.ChunkLoadHandler;
import com.ivomens.mcheli.wingman.handler.ProjectileChunkLoader;
import com.ivomens.mcheli.wingman.handler.UavPreviews;
import com.ivomens.mcheli.wingman.mission.MissionRoutes;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The wingman system of one server: its roster, the ticker that flies the wingmen, the mission routes
 * and the chunk leases for autonomous aircraft, UAVs and projectiles. Every {@link MinecraftServer}
 * owns one (see {@link #of}), so nothing is shared between worlds opened in the same game; when the
 * server stops, {@link #stop} drops everything that is not saved.
 *
 * <p>What is saved lives elsewhere: markers and taxi routes with each level
 * ({@link com.ivomens.mcheli.wingman.airfield.AirfieldData}), mission routes in the config directory.
 * The roster itself is never saved.
 */
public final class WingmanService {

    /** Implemented on {@link MinecraftServer} by a mixin, so every server owns its own service. */
    public interface Holder {
        WingmanService mcheli$wingmen();
    }

    private final Roster roster = new Roster();
    private final MissionRoutes missionRoutes = MissionRoutes.load(Services.PLATFORM.getConfigDir());
    private final WingmanAccess access = new WingmanAccess(this.roster, this.missionRoutes);
    private final WingmanTicker ticker = new WingmanTicker(this.roster);
    private final ChunkLoadHandler chunkLoader = new ChunkLoadHandler(this.roster);
    private final ProjectileChunkLoader projectileChunkLoader = new ProjectileChunkLoader();
    private final UavPreviews uavPreviews = new UavPreviews();

    public static WingmanService of(MinecraftServer server) {
        return ((Holder) server).mcheli$wingmen();
    }

    public static WingmanService of(ServerLevel level) {
        return of(level.getServer());
    }

    public static WingmanService of(ServerPlayer player) {
        return of(player.server);
    }

    public Roster roster() {
        return this.roster;
    }

    /** Who may order which wingman and edit which route. */
    public WingmanAccess access() {
        return this.access;
    }

    public MissionRoutes missionRoutes() {
        return this.missionRoutes;
    }

    public UavPreviews uavPreviews() {
        return this.uavPreviews;
    }

    /** Chunks kept loaded in the level by UAV, wingman and projectile leases, for {@code /mcheli perf}. */
    public int forcedChunks(ServerLevel level) {
        return this.chunkLoader.forcedChunks(level) + this.projectileChunkLoader.forcedChunks(level);
    }

    /** A projectile that keeps chunks loaded announced its path. */
    public void requestProjectilePath(ProjectileEntity projectile) {
        this.projectileChunkLoader.requestPath(projectile);
    }

    /**
     * Forgets the runtime state of a server that stopped: the roster, the combat and rider state and the
     * chunk bookkeeping. Mission routes are files and stay as saved.
     */
    public void stop() {
        this.roster.clear();
        this.ticker.clear();
        this.chunkLoader.clear();
        this.projectileChunkLoader.clear();
        this.uavPreviews.clear();
    }

    // Game events, called through McHeliWingman

    void onServerTick(MinecraftServer server) {
        this.uavPreviews.onServerTick(server);
    }

    void onLevelTickStart(ServerLevel level) {
        this.chunkLoader.onLevelTick(level);
        this.ticker.beforeEntities(level);
    }

    void onLevelTickEnd(ServerLevel level) {
        this.projectileChunkLoader.onLevelTick(level);
        this.ticker.afterEntities(level);
    }

    void onLevelUnload(ServerLevel level) {
        this.chunkLoader.onLevelUnload(level);
        this.projectileChunkLoader.onLevelUnload(level);
    }

    void onEntityJoined(Entity entity, Level level) {
        this.projectileChunkLoader.onEntityJoin(entity, level);
    }

    /** A destroyed wingman frees its formation slot; unloading or changing dimension keeps the entry. */
    void onEntityLeave(Entity entity, Level level) {
        this.projectileChunkLoader.onEntityLeave(entity, level);
        this.chunkLoader.onEntityLeave(entity, level);
        if (entity instanceof AircraftEntity aircraft) {
            Entity.RemovalReason reason = aircraft.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) {
                this.roster.remove(aircraft.getUUID());
            }
        }
    }

    void onPlayerLogout(ServerPlayer player) {
        this.chunkLoader.onPlayerLogout();
        this.uavPreviews.onPlayerLogout(player);
    }

    void onLivingDeath(LivingEntity entity) {
        this.uavPreviews.onLivingDeath(entity);
    }
}
