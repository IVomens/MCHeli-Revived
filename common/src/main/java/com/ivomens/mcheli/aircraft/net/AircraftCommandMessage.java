package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Stores the command string of the aircraft the sender rides or controls. */
public record AircraftCommandMessage(String command) implements ServerboundMessage {

    public static final int MAX_LENGTH = 512;

    private static final StreamCodec<ByteBuf, AircraftCommandMessage> CODEC = StreamCodec.composite(
            Codecs.utf(MAX_LENGTH), AircraftCommandMessage::command,
            AircraftCommandMessage::new);

    public static final MessageType<AircraftCommandMessage> TYPE = MessageType.serverbound("aircraft/command", CODEC);

    @Override
    public MessageType<AircraftCommandMessage> messageType() {
        return TYPE;
    }
}
