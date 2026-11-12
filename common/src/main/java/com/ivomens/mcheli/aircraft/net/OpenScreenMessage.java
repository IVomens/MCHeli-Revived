package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Opens one of the numbered screens of {@link com.ivomens.mcheli.aircraft.net.GuiOpener}. */
public record OpenScreenMessage(int screenId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, OpenScreenMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenScreenMessage::screenId,
            OpenScreenMessage::new);

    public static final MessageType<OpenScreenMessage> TYPE = MessageType.serverbound("gui/open_screen", CODEC);

    @Override
    public MessageType<OpenScreenMessage> messageType() {
        return TYPE;
    }
}
