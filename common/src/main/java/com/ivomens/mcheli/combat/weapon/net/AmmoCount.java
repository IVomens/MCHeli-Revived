package com.ivomens.mcheli.combat.weapon.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Loaded and reserve ammunition of one weapon. */
public record AmmoCount(short ammo, short reserveAmmo) {

    public static final StreamCodec<ByteBuf, AmmoCount> CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT, AmmoCount::ammo,
            ByteBufCodecs.SHORT, AmmoCount::reserveAmmo,
            AmmoCount::new);
}
