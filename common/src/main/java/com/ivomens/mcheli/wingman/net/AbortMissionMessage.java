package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Aborts the mission of one wingman. */
public record AbortMissionMessage(UUID wingmanId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, AbortMissionMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AbortMissionMessage::wingmanId,
            AbortMissionMessage::new);

    public static final MessageType<AbortMissionMessage> TYPE = MessageType.serverbound("wingman/abort_mission", CODEC);

    @Override
    public MessageType<AbortMissionMessage> messageType() {
        return TYPE;
    }
}
