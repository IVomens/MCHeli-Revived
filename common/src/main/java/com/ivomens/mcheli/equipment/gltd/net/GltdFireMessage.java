package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Fires the laser of the designator the sender operates. */
public record GltdFireMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, GltdFireMessage> CODEC = StreamCodec.unit(new GltdFireMessage());

    public static final MessageType<GltdFireMessage> TYPE = MessageType.serverbound("gltd/fire", CODEC);

    @Override
    public MessageType<GltdFireMessage> messageType() {
        return TYPE;
    }
}
