package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Scoreboard screen: teleports the sender to their spawn point. */
public record JumpToSpawnPointMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, JumpToSpawnPointMessage> CODEC =
            StreamCodec.unit(new JumpToSpawnPointMessage());

    public static final MessageType<JumpToSpawnPointMessage> TYPE =
            MessageType.serverbound("multiplay/jump_to_spawn_point", CODEC);

    @Override
    public MessageType<JumpToSpawnPointMessage> messageType() {
        return TYPE;
    }
}
