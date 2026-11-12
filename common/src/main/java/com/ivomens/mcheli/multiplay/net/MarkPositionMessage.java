package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** Marks a block position on the player's HUD. */
public record MarkPositionMessage(BlockPos pos) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, MarkPositionMessage> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MarkPositionMessage::pos,
            MarkPositionMessage::new);

    public static final MessageType<MarkPositionMessage> TYPE =
            MessageType.clientbound("multiplay/mark_position", CODEC);

    @Override
    public MessageType<MarkPositionMessage> messageType() {
        return TYPE;
    }
}
