package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Reloads the launcher in the sender's hand. */
public record LauncherReloadMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LauncherReloadMessage> CODEC =
            StreamCodec.unit(new LauncherReloadMessage());

    public static final MessageType<LauncherReloadMessage> TYPE = MessageType.serverbound("lweapon/reload", CODEC);

    @Override
    public MessageType<LauncherReloadMessage> messageType() {
        return TYPE;
    }
}
