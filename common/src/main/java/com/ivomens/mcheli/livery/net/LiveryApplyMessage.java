package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Puts a livery on an aircraft; an empty id restores the default texture. */
public record LiveryApplyMessage(int aircraftId, String liveryId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LiveryApplyMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LiveryApplyMessage::aircraftId,
            Codecs.utf(Livery.ID_LENGTH), LiveryApplyMessage::liveryId,
            LiveryApplyMessage::new);

    public static final MessageType<LiveryApplyMessage> TYPE = MessageType.serverbound("livery/apply", CODEC);

    @Override
    public MessageType<LiveryApplyMessage> messageType() {
        return TYPE;
    }
}
