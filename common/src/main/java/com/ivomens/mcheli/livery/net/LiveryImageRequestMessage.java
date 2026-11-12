package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Asks for a livery image the client has not cached yet; answered with {@link LiveryImageChunkMessage}s. */
public record LiveryImageRequestMessage(String liveryId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LiveryImageRequestMessage> CODEC = StreamCodec.composite(
            Codecs.utf(Livery.ID_LENGTH), LiveryImageRequestMessage::liveryId,
            LiveryImageRequestMessage::new);

    public static final MessageType<LiveryImageRequestMessage> TYPE =
            MessageType.serverbound("livery/image_request", CODEC);

    @Override
    public MessageType<LiveryImageRequestMessage> messageType() {
        return TYPE;
    }
}
