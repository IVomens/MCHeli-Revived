package com.ivomens.mcheli.inspector.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An operator's inspector asks for the server's snapshot of a vehicle, at most a few times a second. */
public record InspectorRequestMessage(int entityId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, InspectorRequestMessage> CODEC =
            ByteBufCodecs.VAR_INT.map(InspectorRequestMessage::new, InspectorRequestMessage::entityId);

    public static final MessageType<InspectorRequestMessage> TYPE = MessageType.serverbound("inspector/request", CODEC);

    @Override
    public MessageType<InspectorRequestMessage> messageType() {
        return TYPE;
    }
}
