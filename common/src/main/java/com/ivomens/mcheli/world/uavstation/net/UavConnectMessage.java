package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Connects the sender to a UAV paired with the station, so they control it from there. */
public record UavConnectMessage(int stationId, UUID uavId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, UavConnectMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UavConnectMessage::stationId,
            UUIDUtil.STREAM_CODEC, UavConnectMessage::uavId,
            UavConnectMessage::new);

    public static final MessageType<UavConnectMessage> TYPE = MessageType.serverbound("uav/connect", CODEC);

    @Override
    public MessageType<UavConnectMessage> messageType() {
        return TYPE;
    }
}
