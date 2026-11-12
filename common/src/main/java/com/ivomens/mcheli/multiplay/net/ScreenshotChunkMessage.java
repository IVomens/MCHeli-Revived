package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One piece of a screenshot an operator asked for, starting at {@code offset} of {@code totalSize}
 * bytes. Unrequested uploads are refused.
 */
public record ScreenshotChunkMessage(int offset, int totalSize, byte[] data) implements ServerboundMessage {

    /** Largest piece a client sends. */
    public static final int MAX_CHUNK = 32 * 1024;
    /** Largest screenshot the server accepts. */
    public static final int MAX_TOTAL = 16 * 1024 * 1024;

    private static final StreamCodec<ByteBuf, ScreenshotChunkMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScreenshotChunkMessage::offset,
            ByteBufCodecs.VAR_INT, ScreenshotChunkMessage::totalSize,
            Codecs.bytes(MAX_CHUNK), ScreenshotChunkMessage::data,
            ScreenshotChunkMessage::new);

    public static final MessageType<ScreenshotChunkMessage> TYPE =
            MessageType.serverbound("multiplay/screenshot_chunk", CODEC);

    @Override
    public MessageType<ScreenshotChunkMessage> messageType() {
        return TYPE;
    }
}
