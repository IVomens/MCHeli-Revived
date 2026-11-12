package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** The sender jumps out of their aircraft, with a parachute or down a rope. */
public record ParachuteMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ParachuteMessage> CODEC = StreamCodec.unit(new ParachuteMessage());

    public static final MessageType<ParachuteMessage> TYPE = MessageType.serverbound("aircraft/parachute", CODEC);

    @Override
    public MessageType<ParachuteMessage> messageType() {
        return TYPE;
    }
}
