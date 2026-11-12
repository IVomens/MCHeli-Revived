package com.ivomens.mcheli.inspector.net;

import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server's answer to an {@link InspectorRequestMessage}: its snapshot of the vehicle, or why there is
 * none (the snapshot is then empty). A snapshot is at most {@link InspectorSnapshot#MAX_BYTES} long.
 */
public record InspectorReplyMessage(Status status, InspectorSnapshot snapshot) implements ClientboundMessage {

    public enum Status {
        /** The snapshot is the server's view. */
        OK,
        /** Only operators may see the server's view. */
        DENIED,
        /** The server has no vehicle with that id in the player's level. */
        NOT_FOUND
    }

    private static final StreamCodec<ByteBuf, InspectorReplyMessage> CODEC = StreamCodec.composite(
            Codecs.enumOf(Status.class), InspectorReplyMessage::status,
            InspectorSnapshot.CODEC, InspectorReplyMessage::snapshot,
            InspectorReplyMessage::new);

    public static final MessageType<InspectorReplyMessage> TYPE = MessageType.clientbound("inspector/reply", CODEC);

    @Override
    public MessageType<InspectorReplyMessage> messageType() {
        return TYPE;
    }
}
