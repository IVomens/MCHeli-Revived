package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.NetworkHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Payloads through Fabric's networking API. Both directions' payload types and the server receivers
 * are registered during common init; the client receivers wait for {@link #registerClientReceivers},
 * which the client initializer calls, so a dedicated server never loads the client networking classes.
 * <p>
 * Fabric negotiates channels, not versions, so the protocol version is not checked; both sides run the
 * same jar. A client without MCHeli's channels gets no MCHeli payloads, as on NeoForge.
 */
public class FabricNetworkHelper implements NetworkHelper {

    private final List<PayloadSpec<?>> clientbound = new ArrayList<>();

    @Override
    public synchronized void registerPayloads(String protocolVersion, List<PayloadSpec<?>> payloads) {
        for (PayloadSpec<?> spec : payloads) {
            if (spec.onServer() != null) {
                registerServerbound(spec);
            } else {
                registerClientboundType(spec);
                this.clientbound.add(spec);
            }
        }
    }

    private static <T extends CustomPacketPayload> void registerServerbound(PayloadSpec<T> spec) {
        PayloadTypeRegistry.playC2S().register(spec.type(), spec.codec());
        // Fabric runs play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(spec.type(), (payload, context) -> spec.onServer().accept(payload, context.player()));
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(PayloadSpec<T> spec) {
        PayloadTypeRegistry.playS2C().register(spec.type(), spec.codec());
    }

    /** Called by the client initializer; client handlers run on the client thread. */
    public synchronized void registerClientReceivers() {
        for (PayloadSpec<?> spec : this.clientbound) {
            registerClientbound(spec);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(PayloadSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.type(), (payload, context) -> spec.onClient().accept(payload));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** The same players, in the same order, as NeoForge's {@code ChunkMap.getPlayersWatching}. */
    @Override
    public List<ServerPlayer> getPlayersTracking(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return List.of();
        }
        ChunkMap.TrackedEntity tracked = level.getChunkSource().chunkMap.entityMap.get(entity.getId());
        if (tracked == null) {
            return List.of();
        }
        List<ServerPlayer> players = new ArrayList<>(tracked.seenBy.size());
        for (ServerPlayerConnection connection : tracked.seenBy) {
            players.add(connection.getPlayer());
        }
        return Collections.unmodifiableList(players);
    }
}
