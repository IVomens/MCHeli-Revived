package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Scoreboard screen: shuffles the players into the existing teams. */
public record ShuffleTeamsMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ShuffleTeamsMessage> CODEC = StreamCodec.unit(new ShuffleTeamsMessage());

    public static final MessageType<ShuffleTeamsMessage> TYPE =
            MessageType.serverbound("multiplay/shuffle_teams", CODEC);

    @Override
    public MessageType<ShuffleTeamsMessage> messageType() {
        return TYPE;
    }
}
