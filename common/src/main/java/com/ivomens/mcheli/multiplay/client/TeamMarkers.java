package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.multiplay.Multiplay;
import com.ivomens.mcheli.multiplay.TargetMarkerObject;
import com.ivomens.mcheli.core.target.TargetType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the team markers show on this client: the entities the team spotted, each for some seconds, the one
 * point the player marked for the team, and the markers projected for the frame being drawn. Positions are
 * queued while entities render (camera-relative, as from inside a renderer); {@link TargetMarkerOverlay}
 * draws them. One per client.
 */
public final class TeamMarkers {

    /** Reserve value callers pass for a marked point rather than an entity. */
    static final int RESERVE_POINT = 100;
    private static final int SPOT_DECAY_INTERVAL_TICKS = 20;

    private final ScreenProjection projection;
    private final List<Marker> frameMarkers = new ArrayList<>();
    /** Spotted entity id to remaining seconds. */
    private final Map<Integer, Integer> spotted = new HashMap<>();
    private final double[] projected = new double[3];
    private int spotDecayTicks;
    @Nullable
    private MarkPointParticle markPoint;

    public TeamMarkers(ScreenProjection projection) {
        this.projection = projection;
    }

    /** Counts the spotted entities' seconds down while the game runs. */
    public void onClientTick() {
        if (!Minecraft.getInstance().isPaused()) {
            this.spotDecayTicks++;
        }
        if (this.spotDecayTicks >= SPOT_DECAY_INTERVAL_TICKS) {
            this.spotDecayTicks = 0;
            this.spotted.replaceAll((id, seconds) -> seconds > 0 ? seconds - 1 : seconds);
            this.spotted.values().removeIf(seconds -> seconds <= 0);
        }
    }

    public boolean isSpotted(@Nullable Entity entity) {
        return entity != null && this.spotted.containsKey(entity.getId());
    }

    /** The team spotted the entity: it is marked for at least {@code seconds} more. */
    public void spot(int entityId, int seconds) {
        this.spotted.merge(entityId, seconds, Math::max);
    }

    /**
     * Queues a marker for this frame if the target should be marked for the local player.
     *
     * @param reserve {@code 100} for a marked point; any other value for an entity
     * @param x       camera-relative position, as available inside an entity renderer
     */
    public void queue(int reserve, TargetMarkerObject target, double x, double y, double z) {
        if (target == null || !TargetMarkerOverlay.isEnableEntityMarker()) return;
        Minecraft mc = Minecraft.getInstance();
        Player clientPlayer = mc.player;
        if (clientPlayer == null) return;

        TargetType type = reserve == RESERVE_POINT ? TargetType.POINT : this.entityTargetType(clientPlayer,
                target.getEntity());
        if (type == null || type == TargetType.NONE) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double[] p = this.projected;
        if (!this.projection.projectUnclipped(camera.x + x, camera.y + y, camera.z + z, p)) return;
        this.frameMarkers.add(new Marker(type.ordinal(), target, (float) p[0], (float) p[1], p[2] <= 0));
    }

    /** @return {@code null} when the entity must not be marked at all */
    @Nullable
    private TargetType entityTargetType(Player clientPlayer, @Nullable Entity entity) {
        TargetType type = TargetType.NONE;
        if (entity instanceof AircraftEntity ac) {
            if (ac.seats().isAboard(clientPlayer)) return null;
            if (ac.seats().hasAllyOf(clientPlayer)) type = TargetType.SAME_TEAM_PLAYER;
        } else if (entity instanceof Player) {
            if (entity == clientPlayer || entity.getVehicle() instanceof SeatEntity
                    || entity.getVehicle() instanceof AircraftEntity) {
                return null;
            }
            if (clientPlayer.isAlliedTo(entity)) type = TargetType.SAME_TEAM_PLAYER;
        }

        if (type == TargetType.NONE && this.isSpotted(entity)) {
            type = Multiplay.canSpotEntity(clientPlayer, clientPlayer.getX(),
                    clientPlayer.getY() + clientPlayer.getEyeHeight(), clientPlayer.getZ(), entity, false);
        }
        return type;
    }

    /** A new frame starts: the markers of the last one are dropped. */
    public void startFrame() {
        this.frameMarkers.clear();
    }

    /** The markers queued for the frame being drawn. */
    List<Marker> frameMarkers() {
        return this.frameMarkers;
    }

    /** The server marked a point for the player's team; a height below the world clears the mark. */
    public void markPoint(int x, int y, int z) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;
        if (y < Multiplay.CLEAR_MARK_Y) {
            this.clearMarkPoint();
            this.markPoint = new MarkPointParticle(player.level(), 0.5 + x, 1.0 + y, 0.5 + z, player.getTeam(), this);
            Minecraft.getInstance().particleEngine.add(this.markPoint);
        } else {
            this.clearMarkPoint();
        }
    }

    /** Removes the marked point, if there is one. */
    public void clearMarkPoint() {
        if (this.markPoint != null) {
            this.markPoint.setExpired();
            this.markPoint = null;
        }
    }

    /** A marker projected this frame; {@code type} is a {@link TargetType} ordinal. */
    record Marker(int type, TargetMarkerObject target, float x, float y, boolean behind) {
    }
}
