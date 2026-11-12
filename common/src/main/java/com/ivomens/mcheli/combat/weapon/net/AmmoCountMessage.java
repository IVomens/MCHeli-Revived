package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The ammunition of one weapon of an aircraft. */
public record AmmoCountMessage(int aircraftId, int weaponId, AmmoCount count) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, AmmoCountMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AmmoCountMessage::aircraftId,
            ByteBufCodecs.VAR_INT, AmmoCountMessage::weaponId,
            AmmoCount.CODEC, AmmoCountMessage::count,
            AmmoCountMessage::new);

    public static final MessageType<AmmoCountMessage> TYPE = MessageType.clientbound("combat/ammo_count", CODEC);

    @Override
    public MessageType<AmmoCountMessage> messageType() {
        return TYPE;
    }
}
