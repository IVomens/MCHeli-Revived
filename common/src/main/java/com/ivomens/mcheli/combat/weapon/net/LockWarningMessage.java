package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Something locked onto the player or the aircraft they crew. */
public record LockWarningMessage() implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, LockWarningMessage> CODEC = StreamCodec.unit(new LockWarningMessage());

    public static final MessageType<LockWarningMessage> TYPE = MessageType.clientbound("combat/lock_warning", CODEC);

    @Override
    public MessageType<LockWarningMessage> messageType() {
        return TYPE;
    }
}
