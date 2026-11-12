package com.ivomens.mcheli.bench;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** {@code /mcheli benchmark client} was run: the client starts the benchmark ({@code quick}: the short version). */
public record StartClientBenchmarkMessage(boolean quick) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, StartClientBenchmarkMessage> CODEC =
            ByteBufCodecs.BOOL.map(StartClientBenchmarkMessage::new, StartClientBenchmarkMessage::quick);

    public static final MessageType<StartClientBenchmarkMessage> TYPE =
            MessageType.clientbound("bench/start_client", CODEC);

    @Override
    public MessageType<StartClientBenchmarkMessage> messageType() {
        return TYPE;
    }
}
