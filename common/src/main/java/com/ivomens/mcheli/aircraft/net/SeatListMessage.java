package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Entity ids of an aircraft's seats, in seat order; -1 for a missing seat. */
public record SeatListMessage(int aircraftId, List<Integer> seatIds) implements ClientboundMessage {

    public static final int MAX_SEATS = 256;

    private static final StreamCodec<ByteBuf, SeatListMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeatListMessage::aircraftId,
            Codecs.list(ByteBufCodecs.VAR_INT, MAX_SEATS), SeatListMessage::seatIds,
            SeatListMessage::new);

    public static final MessageType<SeatListMessage> TYPE = MessageType.clientbound("aircraft/seat_list", CODEC);

    public SeatListMessage {
        seatIds = List.copyOf(seatIds);
    }

    @Override
    public MessageType<SeatListMessage> messageType() {
        return TYPE;
    }
}
