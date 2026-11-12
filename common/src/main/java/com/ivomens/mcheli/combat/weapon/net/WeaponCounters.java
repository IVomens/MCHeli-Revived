package com.ivomens.mcheli.combat.weapon.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Ammunition and cooldowns of a weapon, in rounds and ticks. */
public record WeaponCounters(short ammo, short reserveAmmo, short cooldown, short reloadCooldown) {

    public static final StreamCodec<ByteBuf, WeaponCounters> CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT, WeaponCounters::ammo,
            ByteBufCodecs.SHORT, WeaponCounters::reserveAmmo,
            ByteBufCodecs.SHORT, WeaponCounters::cooldown,
            ByteBufCodecs.SHORT, WeaponCounters::reloadCooldown,
            WeaponCounters::new);
}
