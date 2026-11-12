package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A piece of a livery image starting at {@code offset} of {@code total} bytes. A total of 0 means the
 * server does not have the image.
 */
public record LiveryImageChunkMessage(String liveryId, int offset, int total, byte[] data)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, LiveryImageChunkMessage> CODEC = StreamCodec.composite(
            Codecs.utf(Livery.ID_LENGTH), LiveryImageChunkMessage::liveryId,
            ByteBufCodecs.VAR_INT, LiveryImageChunkMessage::offset,
            ByteBufCodecs.VAR_INT, LiveryImageChunkMessage::total,
            Codecs.bytes(LiveryManager.MAX_CHUNK), LiveryImageChunkMessage::data,
            LiveryImageChunkMessage::new);

    public static final MessageType<LiveryImageChunkMessage> TYPE =
            MessageType.clientbound("livery/image_chunk", CODEC);

    /** The answer for an image the server does not have, or will not send. */
    public static LiveryImageChunkMessage notFound(String liveryId) {
        return new LiveryImageChunkMessage(liveryId, 0, 0, new byte[0]);
    }

    @Override
    public MessageType<LiveryImageChunkMessage> messageType() {
        return TYPE;
    }
}
