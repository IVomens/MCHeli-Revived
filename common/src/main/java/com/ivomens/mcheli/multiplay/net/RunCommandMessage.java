package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Scoreboard team screen: runs a command (such as {@code team add}) with the sender's own permissions,
 * exactly as if it was typed in chat.
 */
public record RunCommandMessage(String command) implements ServerboundMessage {

    /** The longest command the chat box accepts. */
    public static final int MAX_LENGTH = 256;

    private static final StreamCodec<ByteBuf, RunCommandMessage> CODEC = StreamCodec.composite(
            Codecs.utf(MAX_LENGTH), RunCommandMessage::command,
            RunCommandMessage::new);

    public static final MessageType<RunCommandMessage> TYPE = MessageType.serverbound("multiplay/run_command", CODEC);

    @Override
    public MessageType<RunCommandMessage> messageType() {
        return TYPE;
    }
}
