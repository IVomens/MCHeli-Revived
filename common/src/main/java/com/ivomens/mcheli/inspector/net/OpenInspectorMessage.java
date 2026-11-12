package com.ivomens.mcheli.inspector.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** {@code /mcheli inspect} found a vehicle: the operator's client opens the inspector on it. */
public record OpenInspectorMessage(int entityId) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, OpenInspectorMessage> CODEC =
            ByteBufCodecs.VAR_INT.map(OpenInspectorMessage::new, OpenInspectorMessage::entityId);

    public static final MessageType<OpenInspectorMessage> TYPE = MessageType.clientbound("inspector/open", CODEC);

    @Override
    public MessageType<OpenInspectorMessage> messageType() {
        return TYPE;
    }
}
