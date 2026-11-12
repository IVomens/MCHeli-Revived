package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Gets the operator off the laser designator the sender operates. */
public record GltdDismountMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, GltdDismountMessage> CODEC = StreamCodec.unit(new GltdDismountMessage());

    public static final MessageType<GltdDismountMessage> TYPE = MessageType.serverbound("gltd/dismount", CODEC);

    @Override
    public MessageType<GltdDismountMessage> messageType() {
        return TYPE;
    }
}
