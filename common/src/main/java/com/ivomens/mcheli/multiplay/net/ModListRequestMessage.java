package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An operator asked for the player's mod list; the answer carries {@code requestId} back. */
public record ModListRequestMessage(int requestId) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ModListRequestMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ModListRequestMessage::requestId,
            ModListRequestMessage::new);

    public static final MessageType<ModListRequestMessage> TYPE =
            MessageType.clientbound("multiplay/mod_list_request", CODEC);

    @Override
    public MessageType<ModListRequestMessage> messageType() {
        return TYPE;
    }
}
