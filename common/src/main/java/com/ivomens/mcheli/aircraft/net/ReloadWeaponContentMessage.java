package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Reloads every weapon definition and rebuilds the weapons of every aircraft (development aid). */
public record ReloadWeaponContentMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ReloadWeaponContentMessage> CODEC =
            StreamCodec.unit(new ReloadWeaponContentMessage());

    public static final MessageType<ReloadWeaponContentMessage> TYPE =
            MessageType.serverbound("aircraft/reload_weapon_content", CODEC);

    @Override
    public MessageType<ReloadWeaponContentMessage> messageType() {
        return TYPE;
    }
}
