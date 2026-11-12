package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Deletes a livery; the reply is a fresh list for {@code vehicleType}, the type the screen shows. */
public record LiveryDeleteMessage(String liveryId, String vehicleType) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LiveryDeleteMessage> CODEC = StreamCodec.composite(
            Codecs.utf(Livery.ID_LENGTH), LiveryDeleteMessage::liveryId,
            Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), LiveryDeleteMessage::vehicleType,
            LiveryDeleteMessage::new);

    public static final MessageType<LiveryDeleteMessage> TYPE = MessageType.serverbound("livery/delete", CODEC);

    @Override
    public MessageType<LiveryDeleteMessage> messageType() {
        return TYPE;
    }
}
