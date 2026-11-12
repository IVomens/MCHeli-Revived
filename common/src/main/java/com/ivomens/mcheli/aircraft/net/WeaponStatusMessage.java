package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** The selected weapon of every seat of an aircraft, pilot first. */
public record WeaponStatusMessage(int aircraftId, List<Byte> weaponIds) implements ClientboundMessage {

    public static final int MAX_SEATS = 256;

    private static final StreamCodec<ByteBuf, WeaponStatusMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponStatusMessage::aircraftId,
            Codecs.list(ByteBufCodecs.BYTE, MAX_SEATS), WeaponStatusMessage::weaponIds,
            WeaponStatusMessage::new);

    public static final MessageType<WeaponStatusMessage> TYPE =
            MessageType.clientbound("aircraft/weapon_status", CODEC);

    public WeaponStatusMessage {
        weaponIds = List.copyOf(weaponIds);
    }

    @Override
    public MessageType<WeaponStatusMessage> messageType() {
        return TYPE;
    }
}
