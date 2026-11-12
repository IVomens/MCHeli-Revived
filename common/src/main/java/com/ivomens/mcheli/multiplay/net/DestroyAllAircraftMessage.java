package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Scoreboard screen: destroys every aircraft on the server. */
public record DestroyAllAircraftMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, DestroyAllAircraftMessage> CODEC =
            StreamCodec.unit(new DestroyAllAircraftMessage());

    public static final MessageType<DestroyAllAircraftMessage> TYPE =
            MessageType.serverbound("multiplay/destroy_all_aircraft", CODEC);

    @Override
    public MessageType<DestroyAllAircraftMessage> messageType() {
        return TYPE;
    }
}
