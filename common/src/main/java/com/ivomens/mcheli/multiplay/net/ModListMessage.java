package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * A chunk of the sender's mod list, answering request {@code requestId}. The first chunk of a list
 * gets a header line naming the player when it is shown.
 */
public record ModListMessage(int requestId, boolean firstChunk, List<String> lines) implements ServerboundMessage {

    public static final int MAX_LINES = 512;
    public static final int MAX_LINE_LENGTH = 4096;

    private static final StreamCodec<ByteBuf, ModListMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ModListMessage::requestId,
            ByteBufCodecs.BOOL, ModListMessage::firstChunk,
            Codecs.list(Codecs.utf(MAX_LINE_LENGTH), MAX_LINES), ModListMessage::lines,
            ModListMessage::new);

    public static final MessageType<ModListMessage> TYPE = MessageType.serverbound("multiplay/mod_list", CODEC);

    public ModListMessage {
        lines = List.copyOf(lines);
    }

    @Override
    public MessageType<ModListMessage> messageType() {
        return TYPE;
    }
}
