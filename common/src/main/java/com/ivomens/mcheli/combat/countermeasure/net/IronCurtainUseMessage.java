package com.ivomens.mcheli.combat.countermeasure.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An aircraft's active protection system fired; {@code time} is the tick it is at. */
public record IronCurtainUseMessage(int aircraftId, int time) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, IronCurtainUseMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IronCurtainUseMessage::aircraftId,
            ByteBufCodecs.VAR_INT, IronCurtainUseMessage::time,
            IronCurtainUseMessage::new);

    public static final MessageType<IronCurtainUseMessage> TYPE =
            MessageType.clientbound("combat/iron_curtain_use", CODEC);

    @Override
    public MessageType<IronCurtainUseMessage> messageType() {
        return TYPE;
    }
}
