package com.ivomens.mcheli.aircraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The weapon to switch to (-1 for none) and the flare type to fire (0 for none). */
public record WeaponSelection(byte switchWeapon, byte flareType) {

    public static final StreamCodec<ByteBuf, WeaponSelection> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WeaponSelection::switchWeapon,
            ByteBufCodecs.BYTE, WeaponSelection::flareType,
            WeaponSelection::new);
}
