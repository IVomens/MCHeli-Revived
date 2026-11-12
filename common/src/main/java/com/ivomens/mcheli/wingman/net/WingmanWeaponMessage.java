package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Sets the weapon type a wingman prefers; empty for any. */
public record WingmanWeaponMessage(UUID wingmanId, String weaponType) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, WingmanWeaponMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingmanWeaponMessage::wingmanId,
            WingmanCodecs.STATE, WingmanWeaponMessage::weaponType,
            WingmanWeaponMessage::new);

    public static final MessageType<WingmanWeaponMessage> TYPE = MessageType.serverbound("wingman/weapon", CODEC);

    @Override
    public MessageType<WingmanWeaponMessage> messageType() {
        return TYPE;
    }
}
