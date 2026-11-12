package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Asks for the ammunition of every weapon of an aircraft; answered with an {@link AllAmmoCountsMessage}. */
public record AmmoCountsRequestMessage(int aircraftId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, AmmoCountsRequestMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AmmoCountsRequestMessage::aircraftId,
            AmmoCountsRequestMessage::new);

    public static final MessageType<AmmoCountsRequestMessage> TYPE =
            MessageType.serverbound("combat/ammo_counts_request", CODEC);

    @Override
    public MessageType<AmmoCountsRequestMessage> messageType() {
        return TYPE;
    }
}
