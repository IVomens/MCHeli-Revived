package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Makes an aircraft follow the aircraft the sender is riding, in the next free formation slot. */
public record WingmanFollowMessage(UUID wingmanId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WingmanFollowMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingmanFollowMessage::wingmanId,
            WingmanFollowMessage::new);

    public static final MessageType<WingmanFollowMessage> TYPE = MessageType.serverbound("wingman/follow", CODEC);

    @Override
    public MessageType<WingmanFollowMessage> messageType() {
        return TYPE;
    }
}
