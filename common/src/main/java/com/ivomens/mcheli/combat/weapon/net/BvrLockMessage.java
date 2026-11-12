package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Beyond-visual-range radar lock on a target; a non-positive id clears the lock. */
public record BvrLockMessage(int targetId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, BvrLockMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BvrLockMessage::targetId,
            BvrLockMessage::new);

    public static final MessageType<BvrLockMessage> TYPE = MessageType.serverbound("combat/bvr_lock", CODEC);

    @Override
    public MessageType<BvrLockMessage> messageType() {
        return TYPE;
    }
}
