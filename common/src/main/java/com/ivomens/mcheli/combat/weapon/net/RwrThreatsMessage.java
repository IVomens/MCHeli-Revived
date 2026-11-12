package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** The radar warning receiver threats of the aircraft {@code receiverId}; newer snapshots replace older ones. */
public record RwrThreatsMessage(int receiverId, long snapshotSeq, List<RwrThreat> threats)
        implements ClientboundMessage {

    public static final int MAX_THREATS = 64;

    private static final StreamCodec<ByteBuf, RwrThreatsMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RwrThreatsMessage::receiverId,
            ByteBufCodecs.VAR_LONG, RwrThreatsMessage::snapshotSeq,
            Codecs.list(RwrThreat.CODEC, MAX_THREATS), RwrThreatsMessage::threats,
            RwrThreatsMessage::new);

    public static final MessageType<RwrThreatsMessage> TYPE = MessageType.clientbound("combat/rwr_threats", CODEC);

    public RwrThreatsMessage {
        threats = List.copyOf(threats);
    }

    @Override
    public MessageType<RwrThreatsMessage> messageType() {
        return TYPE;
    }
}
