package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The next piece of the player's livery upload. The client sends one piece per
 * {@link LiveryUploadStatusMessage} it gets back, so a slow server is never flooded.
 */
public record LiveryUploadChunkMessage(int offset, byte[] data) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LiveryUploadChunkMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LiveryUploadChunkMessage::offset,
            Codecs.bytes(LiveryManager.MAX_CHUNK), LiveryUploadChunkMessage::data,
            LiveryUploadChunkMessage::new);

    public static final MessageType<LiveryUploadChunkMessage> TYPE =
            MessageType.serverbound("livery/upload_chunk", CODEC);

    @Override
    public MessageType<LiveryUploadChunkMessage> messageType() {
        return TYPE;
    }
}
