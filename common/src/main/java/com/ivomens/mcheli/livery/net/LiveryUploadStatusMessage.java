package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** How far the player's livery upload got, or how it ended. */
public record LiveryUploadStatusMessage(LiveryManager.UploadStatus.State state, int received, int total, String message)
        implements ClientboundMessage {

    private static final int MAX_MESSAGE_LENGTH = 256;

    private static final StreamCodec<ByteBuf, LiveryUploadStatusMessage> CODEC = StreamCodec.composite(
            Codecs.enumOf(LiveryManager.UploadStatus.State.class), LiveryUploadStatusMessage::state,
            ByteBufCodecs.VAR_INT, LiveryUploadStatusMessage::received,
            ByteBufCodecs.VAR_INT, LiveryUploadStatusMessage::total,
            Codecs.utf(MAX_MESSAGE_LENGTH), LiveryUploadStatusMessage::message,
            LiveryUploadStatusMessage::new);

    public static final MessageType<LiveryUploadStatusMessage> TYPE =
            MessageType.clientbound("livery/upload_status", CODEC);

    public static LiveryUploadStatusMessage of(LiveryManager.UploadStatus status) {
        return new LiveryUploadStatusMessage(status.state(), status.received(), status.total(), status.message());
    }

    @Override
    public MessageType<LiveryUploadStatusMessage> messageType() {
        return TYPE;
    }
}
