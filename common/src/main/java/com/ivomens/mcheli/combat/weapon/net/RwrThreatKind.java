package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a radar warning receiver threat is: the kind of emitter and what it is doing (the
 * {@code RWRThreatEvent} constants), and for a missile what it seeks with ({@link ThreatSeeker#NONE}
 * for a radar).
 */
public record RwrThreatKind(byte emitterKind, byte threatMode, ThreatSeeker seeker) {

    public static final StreamCodec<ByteBuf, RwrThreatKind> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, RwrThreatKind::emitterKind,
            ByteBufCodecs.BYTE, RwrThreatKind::threatMode,
            Codecs.enumOf(ThreatSeeker.class), RwrThreatKind::seeker,
            RwrThreatKind::new);
}
