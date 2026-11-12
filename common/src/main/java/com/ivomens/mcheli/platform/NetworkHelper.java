package com.ivomens.mcheli.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Registers MCHeli's play payloads with the loader and sends them. */
public interface NetworkHelper {

    /**
     * Hands over every payload type. A received payload goes to its spec's handler on the main thread
     * of the receiving side.
     */
    void registerPayloads(String protocolVersion, List<PayloadSpec<?>> payloads);

    void sendToServer(CustomPacketPayload payload);

    /** Skips players whose connection has no MCHeli channel (vanilla clients, fake players). */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Players that currently track the entity, i.e. have it loaded on their client. Empty on a client level. */
    List<ServerPlayer> getPlayersTracking(Entity entity);

    /**
     * One payload type, sent in one direction only. A payload the server receives has an
     * {@code onServer} handler, one the client receives an {@code onClient} handler; the other one is null.
     */
    record PayloadSpec<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                       StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                       @Nullable BiConsumer<T, ServerPlayer> onServer,
                                                       @Nullable Consumer<T> onClient) {

        public PayloadSpec {
            if ((onServer == null) == (onClient == null)) {
                throw new IllegalArgumentException("Payload " + type.id() + " needs exactly one receiving side");
            }
        }
    }
}
