package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** A chunk of another player's mod list, relayed to the operator who asked for it. */
public record ModListRelayMessage(List<String> lines) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ModListRelayMessage> CODEC = StreamCodec.composite(
            Codecs.list(Codecs.utf(ModListMessage.MAX_LINE_LENGTH), ModListMessage.MAX_LINES),
                    ModListRelayMessage::lines,
            ModListRelayMessage::new);

    public static final MessageType<ModListRelayMessage> TYPE =
            MessageType.clientbound("multiplay/mod_list_relay", CODEC);

    public ModListRelayMessage {
        lines = List.copyOf(lines);
    }

    @Override
    public MessageType<ModListRelayMessage> messageType() {
        return TYPE;
    }
}
