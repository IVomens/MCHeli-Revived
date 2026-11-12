package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Sets the airburst distance of the sender's current weapon in the aircraft {@code aircraftId}. */
public record AirburstDistanceMessage(int aircraftId, int distance) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, AirburstDistanceMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AirburstDistanceMessage::aircraftId,
            ByteBufCodecs.VAR_INT, AirburstDistanceMessage::distance,
            AirburstDistanceMessage::new);

    public static final MessageType<AirburstDistanceMessage> TYPE =
            MessageType.serverbound("combat/airburst_distance", CODEC);

    @Override
    public MessageType<AirburstDistanceMessage> messageType() {
        return TYPE;
    }
}
