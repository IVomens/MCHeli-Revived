package com.ivomens.mcheli.networking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A network message: an immutable record that travels in one direction only. Implement
 * {@link ServerboundMessage} or {@link ClientboundMessage}, never this interface directly.
 */
public interface Message extends CustomPacketPayload {

    /** The type the message was declared with, usually a {@code TYPE} constant on the record. */
    MessageType<?> messageType();

    @Override
    default Type<? extends CustomPacketPayload> type() {
        return messageType().payloadType();
    }
}
