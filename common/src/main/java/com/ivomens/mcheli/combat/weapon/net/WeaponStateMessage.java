package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The weapon a seat has selected, with its ammunition and cooldowns. */
public record WeaponStateMessage(int aircraftId, int seatId, int weaponId, WeaponCounters counters)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, WeaponStateMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponStateMessage::aircraftId,
            ByteBufCodecs.VAR_INT, WeaponStateMessage::seatId,
            ByteBufCodecs.VAR_INT, WeaponStateMessage::weaponId,
            WeaponCounters.CODEC, WeaponStateMessage::counters,
            WeaponStateMessage::new);

    public static final MessageType<WeaponStateMessage> TYPE = MessageType.clientbound("combat/weapon_state", CODEC);

    @Override
    public MessageType<WeaponStateMessage> messageType() {
        return TYPE;
    }
}
