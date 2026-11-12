package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One tick of crew input for the tank the sender controls. */
public record TankControlMessage(ControlInput input) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, TankControlMessage> CODEC = StreamCodec.composite(
            ControlInput.CODEC, TankControlMessage::input,
            TankControlMessage::new);

    public static final MessageType<TankControlMessage> TYPE = MessageType.serverbound("aircraft/tank_control", CODEC);

    @Override
    public MessageType<TankControlMessage> messageType() {
        return TYPE;
    }
}
