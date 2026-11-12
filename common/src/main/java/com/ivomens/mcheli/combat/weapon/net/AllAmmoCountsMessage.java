package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** The ammunition of every weapon of an aircraft, in weapon order. */
public record AllAmmoCountsMessage(int aircraftId, List<AmmoCount> counts) implements ClientboundMessage {

    public static final int MAX_WEAPONS = 256;

    private static final StreamCodec<ByteBuf, AllAmmoCountsMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AllAmmoCountsMessage::aircraftId,
            Codecs.list(AmmoCount.CODEC, MAX_WEAPONS), AllAmmoCountsMessage::counts,
            AllAmmoCountsMessage::new);

    public static final MessageType<AllAmmoCountsMessage> TYPE =
            MessageType.clientbound("combat/all_ammo_counts", CODEC);

    public AllAmmoCountsMessage {
        counts = List.copyOf(counts);
    }

    @Override
    public MessageType<AllAmmoCountsMessage> messageType() {
        return TYPE;
    }
}
