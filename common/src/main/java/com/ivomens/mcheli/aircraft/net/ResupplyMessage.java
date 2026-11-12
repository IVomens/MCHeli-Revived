package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Refills one weapon of the sender's aircraft from the aircraft's inventory. */
public record ResupplyMessage(int aircraftId, int weaponId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ResupplyMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ResupplyMessage::aircraftId,
            ByteBufCodecs.VAR_INT, ResupplyMessage::weaponId,
            ResupplyMessage::new);

    public static final MessageType<ResupplyMessage> TYPE = MessageType.serverbound("aircraft/resupply", CODEC);

    @Override
    public MessageType<ResupplyMessage> messageType() {
        return TYPE;
    }
}
