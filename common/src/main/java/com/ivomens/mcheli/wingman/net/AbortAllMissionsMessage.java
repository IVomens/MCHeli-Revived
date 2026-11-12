package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Aborts every autonomous mission the sender may order; for operators that is every mission. */
public record AbortAllMissionsMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, AbortAllMissionsMessage> CODEC =
            StreamCodec.unit(new AbortAllMissionsMessage());

    public static final MessageType<AbortAllMissionsMessage> TYPE =
            MessageType.serverbound("wingman/abort_all_missions", CODEC);

    @Override
    public MessageType<AbortAllMissionsMessage> messageType() {
        return TYPE;
    }
}
