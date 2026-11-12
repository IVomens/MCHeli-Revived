package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One tick of crew input for the plane the sender controls. */
public record PlaneControlMessage(ControlInput input) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, PlaneControlMessage> CODEC = StreamCodec.composite(
            ControlInput.CODEC, PlaneControlMessage::input,
            PlaneControlMessage::new);

    public static final MessageType<PlaneControlMessage> TYPE =
            MessageType.serverbound("aircraft/plane_control", CODEC);

    @Override
    public MessageType<PlaneControlMessage> messageType() {
        return TYPE;
    }
}
