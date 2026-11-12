package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Asks for the wingman panel; answered with a {@link WingmanPanelMessage}. */
public record OpenWingmanPanelMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, OpenWingmanPanelMessage> CODEC =
            StreamCodec.unit(new OpenWingmanPanelMessage());

    public static final MessageType<OpenWingmanPanelMessage> TYPE =
            MessageType.serverbound("wingman/open_panel", CODEC);

    @Override
    public MessageType<OpenWingmanPanelMessage> messageType() {
        return TYPE;
    }
}
