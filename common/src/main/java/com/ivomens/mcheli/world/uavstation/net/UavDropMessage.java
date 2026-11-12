package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Drops a UAV from the station's paired list, so other stations can pair with it. */
public record UavDropMessage(int stationId, UUID uavId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, UavDropMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UavDropMessage::stationId,
            UUIDUtil.STREAM_CODEC, UavDropMessage::uavId,
            UavDropMessage::new);

    public static final MessageType<UavDropMessage> TYPE = MessageType.serverbound("uav/drop", CODEC);

    @Override
    public MessageType<UavDropMessage> messageType() {
        return TYPE;
    }
}
