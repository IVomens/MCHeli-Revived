package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Scoreboard screen: switches PvP on or off for the whole server; operators only. */
public record TogglePvpMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, TogglePvpMessage> CODEC = StreamCodec.unit(new TogglePvpMessage());

    public static final MessageType<TogglePvpMessage> TYPE = MessageType.serverbound("multiplay/toggle_pvp", CODEC);

    @Override
    public MessageType<TogglePvpMessage> messageType() {
        return TYPE;
    }
}
