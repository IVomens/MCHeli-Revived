package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Changes the global formation settings; operators only. */
public record FormationSettingsMessage(FormationSettings settings) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, FormationSettingsMessage> CODEC = StreamCodec.composite(
            FormationSettings.CODEC, FormationSettingsMessage::settings,
            FormationSettingsMessage::new);

    public static final MessageType<FormationSettingsMessage> TYPE =
            MessageType.serverbound("wingman/formation_settings", CODEC);

    @Override
    public MessageType<FormationSettingsMessage> messageType() {
        return TYPE;
    }
}
