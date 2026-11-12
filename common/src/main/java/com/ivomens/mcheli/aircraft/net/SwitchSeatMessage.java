package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Moves the sender to the next or the previous free seat of their aircraft. */
public record SwitchSeatMessage(boolean next) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, SwitchSeatMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SwitchSeatMessage::next,
            SwitchSeatMessage::new);

    public static final MessageType<SwitchSeatMessage> TYPE = MessageType.serverbound("aircraft/switch_seat", CODEC);

    @Override
    public MessageType<SwitchSeatMessage> messageType() {
        return TYPE;
    }
}
