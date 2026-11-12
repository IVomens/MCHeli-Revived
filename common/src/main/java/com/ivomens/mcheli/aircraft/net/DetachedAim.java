package com.ivomens.mcheli.aircraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The pilot's aim of a turret that turns independently of the aircraft, when {@code active}. */
public record DetachedAim(boolean active, float yaw, float pitch) {

    public static final StreamCodec<ByteBuf, DetachedAim> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DetachedAim::active,
            ByteBufCodecs.FLOAT, DetachedAim::yaw,
            ByteBufCodecs.FLOAT, DetachedAim::pitch,
            DetachedAim::new);

    /** This aim with an angle that is not finite replaced by 0. */
    public DetachedAim sanitized() {
        return new DetachedAim(active, WeaponUse.finiteOrZero(yaw), WeaponUse.finiteOrZero(pitch));
    }
}
