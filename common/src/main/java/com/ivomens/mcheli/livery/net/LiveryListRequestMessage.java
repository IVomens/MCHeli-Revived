package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import net.minecraft.network.codec.StreamCodec;

/** Asks for the liveries of a vehicle type plus the player's own; answered with a {@link LiveryListMessage}. */
public record LiveryListRequestMessage(String vehicleType) implements ServerboundMessage {

    public static final MessageType<LiveryListRequestMessage> TYPE = MessageType.serverbound("livery/list_request",
            StreamCodec.composite(
                    Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), LiveryListRequestMessage::vehicleType,
                    LiveryListRequestMessage::new));

    @Override
    public MessageType<LiveryListRequestMessage> messageType() {
        return TYPE;
    }
}
