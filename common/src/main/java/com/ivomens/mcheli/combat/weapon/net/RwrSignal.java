package com.ivomens.mcheli.combat.weapon.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** How strong a radar warning receiver threat is and how sure the receiver is of it, both 0 to 1. */
public record RwrSignal(float strength, float confidence) {

    public static final StreamCodec<ByteBuf, RwrSignal> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RwrSignal::strength,
            ByteBufCodecs.FLOAT, RwrSignal::confidence,
            RwrSignal::new);
}
