package com.ivomens.mcheli.aircraft;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Keeps an aircraft's extra (part) bounding boxes positioned and serves them to entity collision.
 * <p>
 * A parked aircraft with nobody inside or on top of it does not need its boxes recomputed every
 * tick: after a few stationary ticks the boxes are frozen into a cache until the aircraft moves or a
 * player touches them. On the client, players standing on a moving box are carried along with it.
 */
public final class ExtraBoundingBoxTracker {

    private static final int STATIONARY_TICKS_BEFORE_CACHE = 5;
    private static final double MOVE_EPSILON = 1.0E-4;
    private static final double PLAYER_SEARCH_MARGIN = 2.0;
    private static final AABB[] NO_BOXES = new AABB[0];
    /** Up to this many players in the level, they are found by walking the level's list of them. */
    private static final int PLAYER_LIST_LIMIT = 16;
    private static final EntityTypeTest<Entity, Player> PLAYERS = EntityTypeTest.forClass(Player.class);

    private final AircraftEntity aircraft;
    /** Players near the boxes, reused every tick. */
    private final List<Player> nearbyPlayers = new ArrayList<>();

    private AABB[] cachedBoxes;
    private AABB cachedSweepBox;
    /** Reused result of {@link #getCollisionBoxes()} while nothing is cached. */
    private AABB[] liveBoxes = NO_BOXES;

    private int stationaryTicks;
    private boolean sampledTransform;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private float lastRoll;

    public ExtraBoundingBoxTracker(AircraftEntity aircraft) {
        this.aircraft = aircraft;
    }

    public void invalidate() {
        this.cachedBoxes = null;
        this.cachedSweepBox = null;
        this.stationaryTicks = 0;
        this.sampledTransform = false;
    }

    /** The boxes entity collision should use; the returned array must not be kept. */
    public AABB[] getCollisionBoxes() {
        if (this.cachedBoxes != null) {
            return this.cachedBoxes;
        }
        PartBoundingBox[] parts = this.aircraft.partBoxes().boxes();
        if (parts == null || parts.length == 0) {
            return NO_BOXES;
        }
        if (this.liveBoxes.length != parts.length) {
            this.liveBoxes = new AABB[parts.length];
        }
        for (int i = 0; i < parts.length; i++) {
            this.liveBoxes[i] = parts[i].boundingBox();
        }
        return this.liveBoxes;
    }

    /** {@code carryPlayers}: the client owns its player's movement, so platform carrying only runs there. */
    public void tick(boolean carryPlayers) {
        AircraftEntity ac = this.aircraft;
        PartBoundingBox[] parts = ac.partBoxes().boxes();
        if (parts == null || parts.length == 0) {
            invalidate();
            return;
        }

        boolean hasRiders = hasAnyRider();
        boolean transformChanged = hasTransformChanged();
        if (hasRiders || transformChanged) {
            invalidate();
        } else if (this.cachedBoxes != null) {
            if (!isPlayerInsideCachedBoxes()) {
                this.stationaryTicks++;
                return;
            }
            invalidate();
        }

        for (PartBoundingBox part : parts) {
            part.updatePosition(ac.getX(), ac.getY(), ac.getZ(), ac.getYRot(), ac.getXRot(), ac.roll());
        }
        if (carryPlayers) {
            carryPlayersStandingOnBoxes(parts);
        }

        AABB sweepBox = enclosingBox(parts).inflate(PLAYER_SEARCH_MARGIN);
        boolean hasPlayersInside = false;
        collectPlayers(sweepBox);
        for (Player player : this.nearbyPlayers) {
            AABB playerBox = player.getBoundingBox();
            for (PartBoundingBox part : parts) {
                if (part.intersectsAABB(playerBox)) {
                    player.fallDistance = 0.0F;
                    hasPlayersInside = true;
                }
            }
        }
        this.nearbyPlayers.clear();

        updateStationaryState(transformChanged || hasRiders || hasPlayersInside);
        if (!hasRiders && !hasPlayersInside && this.stationaryTicks >= STATIONARY_TICKS_BEFORE_CACHE) {
            cacheBoxes(parts, sweepBox);
        }
    }

    private AABB enclosingBox(PartBoundingBox[] parts) {
        AircraftEntity ac = this.aircraft;
        double minX = ac.getX();
        double minY = ac.getY();
        double minZ = ac.getZ();
        double maxX = minX;
        double maxY = minY;
        double maxZ = minZ;
        for (PartBoundingBox part : parts) {
            AABB box = part.boundingBox();
            if (box != null) {
                minX = Math.min(minX, box.minX);
                minY = Math.min(minY, box.minY);
                minZ = Math.min(minZ, box.minZ);
                maxX = Math.max(maxX, box.maxX);
                maxY = Math.max(maxY, box.maxY);
                maxZ = Math.max(maxZ, box.maxZ);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Fills {@link #nearbyPlayers} with the non-spectator players touching the box, in no particular
     * order. With a few players, walking the level's player list is cheapest; with many, every vehicle
     * walking all of them costs the square of the player count, and the entity sections around the box,
     * which hold the same players, are asked instead.
     */
    private void collectPlayers(AABB box) {
        this.nearbyPlayers.clear();
        Level level = this.aircraft.level();
        List<? extends Player> players = level.players();
        if (players.size() <= PLAYER_LIST_LIMIT) {
            for (Player player : players) {
                if (isTouchingPlayer(player, box)) {
                    this.nearbyPlayers.add(player);
                }
            }
        } else {
            level.getEntities(PLAYERS, box, ExtraBoundingBoxTracker::isCountedPlayer, this.nearbyPlayers);
        }
    }

    private static boolean isTouchingPlayer(Player player, AABB box) {
        return isCountedPlayer(player) && player.getBoundingBox().intersects(box);
    }

    private static boolean isCountedPlayer(Player player) {
        return !player.isSpectator() && !player.isRemoved();
    }

    private void carryPlayersStandingOnBoxes(PartBoundingBox[] parts) {
        Set<Player> carried = null;
        for (PartBoundingBox part : parts) {
            AABB previousBox = part.backupBoundingBox();
            AABB currentBox = part.boundingBox();
            if (previousBox == null || currentBox == null) {
                continue;
            }

            collectPlayers(previousBox.minmax(currentBox).inflate(0.25, 0.75, 0.25));
            for (Player player : this.nearbyPlayers) {
                if (carried != null && carried.contains(player) || player.isPassenger()
                        || this.aircraft.seats().isAboard(player) || !isStandingOn(player, previousBox)) {
                    continue;
                }
                if (movePlayer(player, carryDelta(player, part))) {
                    if (carried == null) {
                        carried = Collections.newSetFromMap(new IdentityHashMap<>());
                    }
                    carried.add(player);
                }
            }
        }
        this.nearbyPlayers.clear();
    }

    private static boolean isStandingOn(Player player, AABB box) {
        final double edge = 0.05;
        AABB playerBox = player.getBoundingBox();
        boolean horizontalOverlap = playerBox.maxX > box.minX + edge && playerBox.minX < box.maxX - edge
                && playerBox.maxZ > box.minZ + edge && playerBox.minZ < box.maxZ - edge;
        boolean nearTop = playerBox.minY >= box.maxY - 0.35 && playerBox.minY <= box.maxY + 0.25;
        if (horizontalOverlap && nearTop) {
            return true;
        }
        AABB feet = new AABB(playerBox.minX + edge, playerBox.minY - 0.20, playerBox.minZ + edge,
                playerBox.maxX - edge, playerBox.minY + 0.05, playerBox.maxZ - edge);
        return box.intersects(feet);
    }

    /** How far the box moved the point under the player: the player's offset is kept in box-local axes. */
    private static Vec3 carryDelta(Player player, PartBoundingBox part) {
        Vec3 previousCenter = part.prevPos() != null ? part.prevPos() : part.nowPos();
        Vec3 currentCenter = part.nowPos() != null ? part.nowPos() : previousCenter;
        double relX = player.getX() - previousCenter.x;
        double relY = player.getY() - previousCenter.y;
        double relZ = player.getZ() - previousCenter.z;

        Vec3 prevAxisX = part.prevAxisX() != null ? part.prevAxisX() : part.axisX();
        Vec3 prevAxisY = part.prevAxisY() != null ? part.prevAxisY() : part.axisY();
        Vec3 prevAxisZ = part.prevAxisZ() != null ? part.prevAxisZ() : part.axisZ();
        double localX = relX * prevAxisX.x + relY * prevAxisX.y + relZ * prevAxisX.z;
        double localY = relX * prevAxisY.x + relY * prevAxisY.y + relZ * prevAxisY.z;
        double localZ = relX * prevAxisZ.x + relY * prevAxisZ.y + relZ * prevAxisZ.z;

        double movedX = part.axisX().x * localX + part.axisY().x * localY + part.axisZ().x * localZ;
        double movedY = part.axisX().y * localX + part.axisY().y * localY + part.axisZ().y * localZ;
        double movedZ = part.axisX().z * localX + part.axisY().z * localY + part.axisZ().z * localZ;
        return new Vec3(currentCenter.x + movedX - player.getX(),
                currentCenter.y + movedY - player.getY(),
                currentCenter.z + movedZ - player.getZ());
    }

    private static boolean movePlayer(Player player, Vec3 delta) {
        if (Math.abs(delta.x) < 1.0E-5 && Math.abs(delta.y) < 1.0E-5 && Math.abs(delta.z) < 1.0E-5) {
            return false;
        }
        player.setPos(player.getX() + delta.x, player.getY() + delta.y, player.getZ() + delta.z);
        player.fallDistance = 0.0F;
        return true;
    }

    private boolean hasAnyRider() {
        if (this.aircraft.getRiddenByEntity() != null) {
            return true;
        }
        for (SeatEntity seat : this.aircraft.seats().all()) {
            if (seat != null && seat.getRiddenByEntity() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTransformChanged() {
        AircraftEntity ac = this.aircraft;
        return !this.sampledTransform
                || Math.abs(ac.getX() - this.lastX) > MOVE_EPSILON
                || Math.abs(ac.getY() - this.lastY) > MOVE_EPSILON
                || Math.abs(ac.getZ() - this.lastZ) > MOVE_EPSILON
                || Math.abs(ac.getYRot() - this.lastYaw) > MOVE_EPSILON
                || Math.abs(ac.getXRot() - this.lastPitch) > MOVE_EPSILON
                || Math.abs(ac.roll() - this.lastRoll) > MOVE_EPSILON;
    }

    private void updateStationaryState(boolean unstable) {
        if (unstable || !this.sampledTransform) {
            this.stationaryTicks = 0;
        } else {
            this.stationaryTicks++;
        }
        AircraftEntity ac = this.aircraft;
        this.sampledTransform = true;
        this.lastX = ac.getX();
        this.lastY = ac.getY();
        this.lastZ = ac.getZ();
        this.lastYaw = ac.getYRot();
        this.lastPitch = ac.getXRot();
        this.lastRoll = ac.roll();
    }

    private boolean isPlayerInsideCachedBoxes() {
        if (this.cachedSweepBox == null) {
            return false;
        }
        collectPlayers(this.cachedSweepBox);
        boolean inside = false;
        for (Player player : this.nearbyPlayers) {
            AABB playerBox = player.getBoundingBox();
            for (AABB box : this.cachedBoxes) {
                if (box != null && box.intersects(playerBox)) {
                    inside = true;
                }
            }
        }
        this.nearbyPlayers.clear();
        return inside;
    }

    private void cacheBoxes(PartBoundingBox[] parts, AABB sweepBox) {
        AABB[] boxes = new AABB[parts.length];
        for (int i = 0; i < parts.length; i++) {
            boxes[i] = parts[i].boundingBox();
        }
        // AABBs are immutable and the parts replace theirs on every update, so no copies are needed
        this.cachedBoxes = boxes;
        this.cachedSweepBox = sweepBox;
    }
}
