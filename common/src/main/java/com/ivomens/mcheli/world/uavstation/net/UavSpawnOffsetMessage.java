package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Where, relative to the station the sender is in, the station places UAVs it launches. */
public record UavSpawnOffsetMessage(byte x, byte y, byte z) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, UavSpawnOffsetMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, UavSpawnOffsetMessage::x,
            ByteBufCodecs.BYTE, UavSpawnOffsetMessage::y,
            ByteBufCodecs.BYTE, UavSpawnOffsetMessage::z,
            UavSpawnOffsetMessage::new);

    public static final MessageType<UavSpawnOffsetMessage> TYPE = MessageType.serverbound("uav/spawn_offset", CODEC);

    @Override
    public MessageType<UavSpawnOffsetMessage> messageType() {
        return TYPE;
    }
}
