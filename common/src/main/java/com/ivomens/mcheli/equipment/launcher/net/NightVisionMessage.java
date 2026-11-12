package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Turns the launcher sight's night vision on or off. */
public record NightVisionMessage(boolean on) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, NightVisionMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NightVisionMessage::on,
            NightVisionMessage::new);

    public static final MessageType<NightVisionMessage> TYPE = MessageType.serverbound("lweapon/night_vision", CODEC);

    @Override
    public MessageType<NightVisionMessage> messageType() {
        return TYPE;
    }
}
