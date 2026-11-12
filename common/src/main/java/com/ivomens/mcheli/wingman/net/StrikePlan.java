package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Where an order sends the wingman and how it attacks there. */
public record StrikePlan(double targetX, double targetZ, double orbitRadius, boolean orbitAttack, int strikePasses) {

    public static final StreamCodec<ByteBuf, StrikePlan> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, StrikePlan::targetX,
            ByteBufCodecs.DOUBLE, StrikePlan::targetZ,
            ByteBufCodecs.DOUBLE, StrikePlan::orbitRadius,
            ByteBufCodecs.BOOL, StrikePlan::orbitAttack,
            ByteBufCodecs.INT, StrikePlan::strikePasses,
            StrikePlan::new);
}
