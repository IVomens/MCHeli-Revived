package com.ivomens.mcheli.networking;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The extra spawn data of a {@link SpawnDataEntity} the player just started tracking. */
public record EntitySpawnDataMessage(int entityId, byte[] data) implements ClientboundMessage {

    public static final int MAX_DATA_LENGTH = 32767;

    private static final StreamCodec<ByteBuf, EntitySpawnDataMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntitySpawnDataMessage::entityId,
            Codecs.bytes(MAX_DATA_LENGTH), EntitySpawnDataMessage::data,
            EntitySpawnDataMessage::new);

    public static final MessageType<EntitySpawnDataMessage> TYPE =
            MessageType.clientbound("networking/entity_spawn_data", CODEC);

    @Override
    public MessageType<EntitySpawnDataMessage> messageType() {
        return TYPE;
    }
}
