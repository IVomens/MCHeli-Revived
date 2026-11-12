package com.ivomens.mcheli.combat.weapon.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a laser beam looks: colour, width, lifetime in ticks, whether it pulses, and how far from the
 * muzzle it starts being drawn.
 */
public record LaserBeamStyle(int argb, float width, int life, boolean pulsate, double renderStartDist) {

    public static final StreamCodec<ByteBuf, LaserBeamStyle> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LaserBeamStyle::argb,
            ByteBufCodecs.FLOAT, LaserBeamStyle::width,
            ByteBufCodecs.VAR_INT, LaserBeamStyle::life,
            ByteBufCodecs.BOOL, LaserBeamStyle::pulsate,
            ByteBufCodecs.DOUBLE, LaserBeamStyle::renderStartDist,
            LaserBeamStyle::new);
}
