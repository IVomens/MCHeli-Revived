package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.NetworkHelper;
import com.ivomens.mcheli.util.state.RegistryHolder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public class NeoForgeNetworkHelper implements NetworkHelper {

    /** Handed over by the common setup during mod construction, read when the payload event fires. */
    @RegistryHolder
    private static String protocolVersion;
    @RegistryHolder
    private static List<PayloadSpec<?>> payloads = List.of();

    @Override
    public void registerPayloads(String protocolVersion, List<PayloadSpec<?>> payloads) {
        NeoForgeNetworkHelper.protocolVersion = protocolVersion;
        NeoForgeNetworkHelper.payloads = List.copyOf(payloads);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public List<ServerPlayer> getPlayersTracking(Entity entity) {
        return entity.level() instanceof ServerLevel level
                ? level.getChunkSource().chunkMap.getPlayersWatching(entity)
                : List.of();
    }

    /** Mod-bus listener; the payloads were handed over during common init. */
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(protocolVersion);
        for (PayloadSpec<?> spec : payloads) {
            register(registrar, spec);
        }
    }

    private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, PayloadSpec<T> spec) {
        IPayloadHandler<T> handler = (payload, context) -> handle(spec, payload, context);
        if (spec.onServer() != null) {
            registrar.playToServer(spec.type(), spec.codec(), handler);
        } else {
            registrar.playToClient(spec.type(), spec.codec(), handler);
        }
    }

    /** Runs on the main thread of the receiving side (payload handlers default to it). */
    private static <T extends CustomPacketPayload> void handle(PayloadSpec<T> spec, T payload, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            if (spec.onServer() != null && context.player() instanceof ServerPlayer player) {
                spec.onServer().accept(payload, player);
            }
        } else if (spec.onClient() != null) {
            spec.onClient().accept(payload);
        }
    }
}
