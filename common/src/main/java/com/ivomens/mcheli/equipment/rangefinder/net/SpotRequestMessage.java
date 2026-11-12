package com.ivomens.mcheli.equipment.rangefinder.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The range finder was used. A zero filter marks the looked-at point; any other filter spots the
 * matching entities in the view cone and costs one use of the range finder.
 */
public record SpotRequestMessage(int targetFilter) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, SpotRequestMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpotRequestMessage::targetFilter,
            SpotRequestMessage::new);

    public static final MessageType<SpotRequestMessage> TYPE = MessageType.serverbound("multiplay/spot_request", CODEC);

    @Override
    public MessageType<SpotRequestMessage> messageType() {
        return TYPE;
    }
}
