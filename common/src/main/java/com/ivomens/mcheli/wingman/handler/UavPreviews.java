package com.ivomens.mcheli.wingman.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which UAV each player is previewing in an open UAV-station screen. The selection is registered
 * by {@link com.ivomens.mcheli.world.uavstation.net.UavPreviewMessage}, refreshed by client heartbeats and
 * expires once the screen closes.
 *
 * <p>Terrain around a far UAV is not streamed to the operator: the client only accepts chunks inside
 * the view square around the player, so the UAV camera shows what the operator's client has loaded.
 * The UAV itself keeps simulating far away through {@link ChunkLoadHandler} when chunk loading is on.
 */
public final class UavPreviews {

    /** A preview target expires this many ticks after its last heartbeat (screen closed). */
    private static final int PREVIEW_TIMEOUT_TICKS = 60;

    private final Map<UUID, PreviewReq> previews = new ConcurrentHashMap<>();

    /** Registers/refreshes the UAV a player is previewing in the station screen. */
    public void setPreview(UUID playerId, UUID uavId, long worldTick) {
        if (playerId != null && uavId != null) {
            previews.put(playerId, new PreviewReq(uavId, worldTick));
        }
    }

    public void clearPreview(UUID playerId) {
        if (playerId != null) {
            previews.remove(playerId);
        }
    }

    /** True if this player is previewing the given UAV. */
    public boolean isPreviewedBy(UUID uavId, UUID playerId) {
        if (uavId == null || playerId == null) {
            return false;
        }
        PreviewReq req = previews.get(playerId);
        return req != null && uavId.equals(req.uavId());
    }

    /** Expires preview targets whose screen stopped sending heartbeats (checked once a second). */
    public void onServerTick(MinecraftServer server) {
        if (previews.isEmpty()) return;
        if (server.getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, PreviewReq>> it = previews.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PreviewReq> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || player.level().getGameTime() - e.getValue().heartbeatTick() > PREVIEW_TIMEOUT_TICKS) {
                it.remove();
            }
        }
    }

    public void clear() {
        previews.clear();
    }

    public void onPlayerLogout(Player player) {
        clearPreview(player.getUUID());
    }

    /** Death swaps in a new player object on respawn; drop the preview of the dying one. */
    public void onLivingDeath(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            clearPreview(player.getUUID());
        }
    }

    private record PreviewReq(UUID uavId, long heartbeatTick) {
    }
}
