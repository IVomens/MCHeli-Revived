package com.ivomens.mcheli.combat.countermeasure.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An aircraft fired chaff; {@code time} is the remaining effect time in ticks. */
public record ChaffUseMessage(int aircraftId, int time) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ChaffUseMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChaffUseMessage::aircraftId,
            ByteBufCodecs.VAR_INT, ChaffUseMessage::time,
            ChaffUseMessage::new);

    public static final MessageType<ChaffUseMessage> TYPE = MessageType.clientbound("combat/chaff_use", CODEC);

    @Override
    public MessageType<ChaffUseMessage> messageType() {
        return TYPE;
    }
}
