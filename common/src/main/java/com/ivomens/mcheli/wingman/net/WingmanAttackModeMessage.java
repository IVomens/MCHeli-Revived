package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Lets a wingman attack on its own, or makes it hold fire. */
public record WingmanAttackModeMessage(UUID wingmanId, boolean autoAttack) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WingmanAttackModeMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingmanAttackModeMessage::wingmanId,
            ByteBufCodecs.BOOL, WingmanAttackModeMessage::autoAttack,
            WingmanAttackModeMessage::new);

    public static final MessageType<WingmanAttackModeMessage> TYPE =
            MessageType.serverbound("wingman/attack_mode", CODEC);

    @Override
    public MessageType<WingmanAttackModeMessage> messageType() {
        return TYPE;
    }
}
