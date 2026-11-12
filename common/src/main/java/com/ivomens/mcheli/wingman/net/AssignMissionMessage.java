package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Sends a wingman along a saved mission route. */
public record AssignMissionMessage(UUID wingmanId, String routeName) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, AssignMissionMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AssignMissionMessage::wingmanId,
            WingmanCodecs.NAME, AssignMissionMessage::routeName,
            AssignMissionMessage::new);

    public static final MessageType<AssignMissionMessage> TYPE =
            MessageType.serverbound("wingman/assign_mission", CODEC);

    @Override
    public MessageType<AssignMissionMessage> messageType() {
        return TYPE;
    }
}
