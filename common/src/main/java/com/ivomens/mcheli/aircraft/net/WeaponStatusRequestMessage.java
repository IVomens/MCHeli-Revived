package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Asks for the selected weapon of every seat of an aircraft; answered with a {@link WeaponStatusMessage}. */
public record WeaponStatusRequestMessage(int aircraftId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WeaponStatusRequestMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponStatusRequestMessage::aircraftId,
            WeaponStatusRequestMessage::new);

    public static final MessageType<WeaponStatusRequestMessage> TYPE =
            MessageType.serverbound("aircraft/weapon_status_request", CODEC);

    @Override
    public MessageType<WeaponStatusRequestMessage> messageType() {
        return TYPE;
    }
}
