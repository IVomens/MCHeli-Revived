package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** Opens the configuration screen of the marker block at {@code pos}. */
public record OpenMarkerScreenMessage(BlockPos pos, MarkerSettings settings) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, OpenMarkerScreenMessage> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenMarkerScreenMessage::pos,
            MarkerSettings.CODEC, OpenMarkerScreenMessage::settings,
            OpenMarkerScreenMessage::new);

    public static final MessageType<OpenMarkerScreenMessage> TYPE =
            MessageType.clientbound("wingman/open_marker_screen", CODEC);

    @Override
    public MessageType<OpenMarkerScreenMessage> messageType() {
        return TYPE;
    }
}
