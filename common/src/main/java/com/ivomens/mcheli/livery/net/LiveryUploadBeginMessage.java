package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Starts a livery upload of {@code totalBytes}; the image follows in {@link LiveryUploadChunkMessage}s. */
public record LiveryUploadBeginMessage(String vehicleType, String name, int totalBytes) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LiveryUploadBeginMessage> CODEC = StreamCodec.composite(
            Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), LiveryUploadBeginMessage::vehicleType,
            Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), LiveryUploadBeginMessage::name,
            ByteBufCodecs.VAR_INT, LiveryUploadBeginMessage::totalBytes,
            LiveryUploadBeginMessage::new);

    public static final MessageType<LiveryUploadBeginMessage> TYPE =
            MessageType.serverbound("livery/upload_begin", CODEC);

    @Override
    public MessageType<LiveryUploadBeginMessage> messageType() {
        return TYPE;
    }
}
