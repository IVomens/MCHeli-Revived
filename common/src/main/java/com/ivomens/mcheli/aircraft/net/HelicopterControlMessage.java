package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One tick of crew input for the helicopter the sender controls. */
public record HelicopterControlMessage(ControlInput input, VehicleExtras extras) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, HelicopterControlMessage> CODEC = StreamCodec.composite(
            ControlInput.CODEC, HelicopterControlMessage::input,
            VehicleExtras.CODEC, HelicopterControlMessage::extras,
            HelicopterControlMessage::new);

    public static final MessageType<HelicopterControlMessage> TYPE =
            MessageType.serverbound("aircraft/helicopter_control", CODEC);

    @Override
    public MessageType<HelicopterControlMessage> messageType() {
        return TYPE;
    }
}
