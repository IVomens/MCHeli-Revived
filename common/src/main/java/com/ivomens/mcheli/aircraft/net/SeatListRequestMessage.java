package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Asks for the seat entities of an aircraft; answered with a {@link SeatListMessage}. */
public record SeatListRequestMessage(int aircraftId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, SeatListRequestMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeatListRequestMessage::aircraftId,
            SeatListRequestMessage::new);

    public static final MessageType<SeatListRequestMessage> TYPE =
            MessageType.serverbound("aircraft/seat_list_request", CODEC);

    @Override
    public MessageType<SeatListRequestMessage> messageType() {
        return TYPE;
    }
}
