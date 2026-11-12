package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** Applies the changes made in the marker screen to the marker block at {@code pos}. */
public record UpdateMarkerMessage(BlockPos pos, MarkerSettings settings) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, UpdateMarkerMessage> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateMarkerMessage::pos,
            MarkerSettings.CODEC, UpdateMarkerMessage::settings,
            UpdateMarkerMessage::new);

    public static final MessageType<UpdateMarkerMessage> TYPE = MessageType.serverbound("wingman/update_marker", CODEC);

    @Override
    public MessageType<UpdateMarkerMessage> messageType() {
        return TYPE;
    }
}
