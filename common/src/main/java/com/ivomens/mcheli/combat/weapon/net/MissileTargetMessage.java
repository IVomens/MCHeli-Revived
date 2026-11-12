package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Retargets a guided missile the sender fired; a target id of 0 clears the target. */
public record MissileTargetMessage(int targetId, int missileId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, MissileTargetMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MissileTargetMessage::targetId,
            ByteBufCodecs.VAR_INT, MissileTargetMessage::missileId,
            MissileTargetMessage::new);

    public static final MessageType<MissileTargetMessage> TYPE =
            MessageType.serverbound("combat/missile_target", CODEC);

    @Override
    public MessageType<MissileTargetMessage> messageType() {
        return TYPE;
    }
}
