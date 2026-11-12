package com.ivomens.mcheli.combat.explosion.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a client needs of the explosions it is shown: whether its particle style draws them from the
 * blocks they affected (the legacy style does; the modern style draws a whole explosion from its
 * position and size). Sent when the client joins a server and whenever the style changes; until then
 * the server takes the default style, the modern one.
 */
public record ExplosionDetailMessage(boolean blocks) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ExplosionDetailMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExplosionDetailMessage::blocks,
            ExplosionDetailMessage::new);

    public static final MessageType<ExplosionDetailMessage> TYPE =
            MessageType.serverbound("combat/explosion_detail", CODEC);

    @Override
    public MessageType<ExplosionDetailMessage> messageType() {
        return TYPE;
    }
}
