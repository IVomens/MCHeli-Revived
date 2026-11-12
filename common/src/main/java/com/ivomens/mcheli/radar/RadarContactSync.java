package com.ivomens.mcheli.radar;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.combat.guidance.ExternalTargets;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactsMessage;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends each player the radar contacts around them. Every {@code RadarUpdateInterval} ticks the tracked entities
 * (players, lockable MCHeli entities and the targets addons registered) are snapshotted on the server thread;
 * the per-player range filtering and message building run on virtual threads. One per server ({@link #of}),
 * closed when the server stops.
 * <p>
 * Only players aboard a vehicle get contacts: the radar is drawn by the vehicle's instruments, and nothing
 * else reads it. Each of them gets what changed since their last snapshot ({@link RadarReceiver}).
 */
public final class RadarContactSync implements AutoCloseable {

    /** Radar contacts are only sent to players within this horizontal distance. */
    private static final double RADAR_RANGE = 2000.0;
    /** Cached contacts not refreshed for this long are dropped. */
    private static final long CACHE_EXPIRY_MS = 5_000L;
    /** Planes, helicopters and chaff closer to the ground than this are hidden by ground clutter. */
    private static final int MIN_HEIGHT_ABOVE_GROUND = 30;
    /** Squared speed (blocks per tick) below which low flyers are not shown. */
    private static final double MIN_SPEED_SQR = 0.5 * 0.5;

    /** The contacts of the last snapshots by entity id, kept until they expire. */
    private final Map<Integer, EntityInfo> contacts = new ConcurrentHashMap<>();
    /** The players aboard a vehicle at the last snapshot, by UUID. */
    private final Map<UUID, RadarReceiver> receivers = new ConcurrentHashMap<>();
    private final ExecutorService radarExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private int tickCounter;
    private long snapshotSeq = 0L;

    /** Implemented on {@link MinecraftServer} by a mixin, so every server syncs its own contacts. */
    public interface Holder {
        RadarContactSync mcheli$radarContacts();
    }

    public static RadarContactSync of(MinecraftServer server) {
        return ((Holder) server).mcheli$radarContacts();
    }

    /** Called at the end of every server tick. */
    public void onServerTick(MinecraftServer server) {
        tickCounter++;

        int interval = Math.max(1, McheliConfig.network().radarUpdateInterval());
        if (tickCounter % interval == 0) {
            snapshotSeq++;
            dispatchAsyncRadarSync(server);
        }
    }

    /** The server stopped: the threads still building messages finish, no new ones start. */
    @Override
    public void close() {
        this.radarExecutor.shutdown();
        this.contacts.clear();
        this.receivers.clear();
    }

    private void dispatchAsyncRadarSync(MinecraftServer mcServer) {

        List<PlayerSnapshot> players = receivingPlayers(mcServer);
        if (players.isEmpty()) {
            this.contacts.clear();
            return;
        }

        long roundStart = System.currentTimeMillis();
        List<VehicleSnapshot> vehicles = new ArrayList<>();
        for (ServerLevel world : mcServer.getAllLevels()) {
            // Contacts are only sent to players in the same dimension.
            if (world.players().isEmpty()) {
                continue;
            }
            ExternalTargets external = ExternalTargets.get();
            for (Entity entity : world.getAllEntities()) {
                RadarContactKind kind = trackedKind(world, entity, external);
                if (kind != null) {
                    EntityInfo info = EntityInfo.of(entity, kind);
                    this.contacts.put(entity.getId(), info);
                    vehicles.add(new VehicleSnapshot(info, info.worldName(), entity.getX(), entity.getZ()));
                }
            }
        }

        cleanupCache(mcServer, roundStart);

        final long currentSeq = this.snapshotSeq;

        for (PlayerSnapshot player : players) {
            radarExecutor.submit(() -> computeAndSendRadar(player, vehicles, currentSeq));
        }
    }

    /** The players that can show radar contacts, with their receivers; forgets everyone else. */
    private List<PlayerSnapshot> receivingPlayers(MinecraftServer server) {
        List<PlayerSnapshot> players = new ArrayList<>();
        Set<UUID> aboard = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RiddenVehicle.of(player) == null) {
                continue;
            }
            RadarReceiver receiver = this.receivers.get(player.getUUID());
            if (receiver == null || receiver.player() != player) {
                // a new player, or one that came back with a new client
                receiver = new RadarReceiver(player);
                this.receivers.put(player.getUUID(), receiver);
            }
            aboard.add(player.getUUID());
            players.add(new PlayerSnapshot(receiver, player.level().dimension().location().toString(),
                    player.position()));
        }
        this.receivers.keySet().retainAll(aboard);
        return players;
    }

    private void computeAndSendRadar(PlayerSnapshot player, List<VehicleSnapshot> vehicles, long seq) {
        try {
            List<EntityInfo> visibleEntities = new ArrayList<>();
            double maxDistSq = RADAR_RANGE * RADAR_RANGE;

            for (VehicleSnapshot vehicle : vehicles) {
                if (!vehicle.dimension.equals(player.dimension)) {
                    continue;
                }
                double dx = player.position.x - vehicle.x;
                double dz = player.position.z - vehicle.z;

                if ((dx * dx + dz * dz) <= maxDistSq) {
                    visibleEntities.add(vehicle.info);
                }
            }

            visibleEntities.sort(Comparator.comparingInt(EntityInfo::entityId));
            if (visibleEntities.size() > RadarContactsMessage.MAX_CONTACTS) {
                visibleEntities = visibleEntities.subList(0, RadarContactsMessage.MAX_CONTACTS);
            }
            RadarContactsMessage message = player.receiver.next(seq, player.dimension, player.position,
                    visibleEntities, System.currentTimeMillis());
            if (message != null) {
                Network.sendToPlayer(player.receiver.player(), message);
            }
        } catch (Throwable t) {
            ModLogger.error("Radar sync failed", t);
        }
    }

    private void cleanupCache(MinecraftServer mcServer, long roundStart) {
        long now = System.currentTimeMillis();
        this.contacts.entrySet().removeIf(entry -> {
            EntityInfo info = entry.getValue();
            if (info.lastUpdateTime() >= roundStart) {
                // Snapshotted this round, so the entity is alive.
                return false;
            }
            if (now - info.lastUpdateTime() > CACHE_EXPIRY_MS) {
                return true;
            }
            Entity entity = getEntityFromAnyWorld(mcServer, info.entityId());
            return entity == null || entity.isRemoved();
        });
    }

    private Entity getEntityFromAnyWorld(MinecraftServer mcServer, int entityId) {
        for (ServerLevel world : mcServer.getAllLevels()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * What the entity shows up as on radar, or null when it does not. An addon's target shows up while its radar
     * cross-section is above 0, an excluded entity never; both only cost anything while the API has registrations.
     */
    @Nullable
    public static RadarContactKind trackedKind(ServerLevel world, Entity entity, ExternalTargets external) {
        if (entity.isRemoved()) return null;

        RadarContactKind externalKind = null;
        if (external.isActive()) {
            if (external.isExcluded(entity)) return null;
            TargetSignature signature = external.signature(entity);
            if (signature != null && signature.radarCrossSection() > 0.0F) {
                externalKind = EntityInfo.kindOf(signature.shownAs());
            }
        }
        if (externalKind == null && !isRadarVisible(entity)) return null;

        boolean flies = externalKind == RadarContactKind.PLANE || externalKind == RadarContactKind.HELICOPTER;
        if (flies || entity instanceof PlaneEntity || entity instanceof HelicopterEntity || entity instanceof ChaffEntity) {
            int ground = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ()));
            int heightAboveGround = (int) (entity.getY() - ground);

            if (heightAboveGround < MIN_HEIGHT_ABOVE_GROUND) return null;

            Vec3 motion = entity.getDeltaMovement();
            if (motion.lengthSqr() < MIN_SPEED_SQR) return null;
        }

        return externalKind != null ? externalKind : EntityInfo.kindOf(entity);
    }

    /** Players, vehicles, decoys and air-to-air missiles show up on radar. */
    private static boolean isRadarVisible(Entity entity) {
        return entity instanceof Player || entity instanceof AircraftEntity || entity instanceof FlareEntity
                || entity instanceof ChaffEntity
                || entity instanceof ProjectileEntity missile && missile.kind() == ProjectileKind.AA_MISSILE;
    }

    private record PlayerSnapshot(RadarReceiver receiver, String dimension, Vec3 position) {
    }

    private record VehicleSnapshot(EntityInfo info, String dimension, double x, double z) {
    }
}
