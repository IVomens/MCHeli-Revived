package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The player's shot hit something. {@code aircraftId} is the aircraft it was fired from, or -1 on foot. */
public record HitMarkerMessage(int aircraftId) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, HitMarkerMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HitMarkerMessage::aircraftId,
            HitMarkerMessage::new);

    public static final MessageType<HitMarkerMessage> TYPE = MessageType.clientbound("combat/hit_marker", CODEC);

    @Override
    public MessageType<HitMarkerMessage> messageType() {
        return TYPE;
    }
}
