package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** An operator asked for the player's screen; the client answers with {@link ScreenshotChunkMessage}s. */
public record ScreenshotRequestMessage() implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ScreenshotRequestMessage> CODEC =
            StreamCodec.unit(new ScreenshotRequestMessage());

    public static final MessageType<ScreenshotRequestMessage> TYPE =
            MessageType.clientbound("multiplay/screenshot_request", CODEC);

    @Override
    public MessageType<ScreenshotRequestMessage> messageType() {
        return TYPE;
    }
}
