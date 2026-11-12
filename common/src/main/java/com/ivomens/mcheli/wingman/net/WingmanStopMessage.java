package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Releases one wingman. */
public record WingmanStopMessage(UUID wingmanId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WingmanStopMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingmanStopMessage::wingmanId,
            WingmanStopMessage::new);

    public static final MessageType<WingmanStopMessage> TYPE = MessageType.serverbound("wingman/stop", CODEC);

    @Override
    public MessageType<WingmanStopMessage> messageType() {
        return TYPE;
    }
}
