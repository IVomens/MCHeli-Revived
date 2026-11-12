package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The TV-guided missile an aircraft just fired, so its gunner can steer it. */
public record TvMissileMessage(int aircraftId, int missileId) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, TvMissileMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TvMissileMessage::aircraftId,
            ByteBufCodecs.VAR_INT, TvMissileMessage::missileId,
            TvMissileMessage::new);

    public static final MessageType<TvMissileMessage> TYPE = MessageType.clientbound("combat/tv_missile", CODEC);

    @Override
    public MessageType<TvMissileMessage> messageType() {
        return TYPE;
    }
}
