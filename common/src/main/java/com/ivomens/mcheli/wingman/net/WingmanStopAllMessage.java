package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Releases every wingman following the aircraft the sender is riding. */
public record WingmanStopAllMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WingmanStopAllMessage> CODEC =
            StreamCodec.unit(new WingmanStopAllMessage());

    public static final MessageType<WingmanStopAllMessage> TYPE = MessageType.serverbound("wingman/stop_all", CODEC);

    @Override
    public MessageType<WingmanStopAllMessage> messageType() {
        return TYPE;
    }
}
