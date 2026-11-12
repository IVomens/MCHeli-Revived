package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A title message from {@code /mcheli title}, shown for {@code showTime} ticks at {@code position}. */
public record TitleMessage(Component text, int showTime, int position) implements ClientboundMessage {

    private static final StreamCodec<RegistryFriendlyByteBuf, TitleMessage> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, TitleMessage::text,
            ByteBufCodecs.VAR_INT, TitleMessage::showTime,
            ByteBufCodecs.VAR_INT, TitleMessage::position,
            TitleMessage::new);

    public static final MessageType<TitleMessage> TYPE = MessageType.clientbound("multiplay/title", CODEC);

    @Override
    public MessageType<TitleMessage> messageType() {
        return TYPE;
    }
}
