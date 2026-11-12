package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One tick of crew input for the ship the sender controls. */
public record ShipControlMessage(ControlInput input) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ShipControlMessage> CODEC = StreamCodec.composite(
            ControlInput.CODEC, ShipControlMessage::input,
            ShipControlMessage::new);

    public static final MessageType<ShipControlMessage> TYPE = MessageType.serverbound("aircraft/ship_control", CODEC);

    @Override
    public MessageType<ShipControlMessage> messageType() {
        return TYPE;
    }
}
