package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One radar warning receiver threat: the emitter, what kind it is, what it is doing and, for a missile,
 * what it seeks with ({@link RwrThreatKind}), and where it comes from.
 */
public record RwrThreat(int emitterId, RwrThreatKind kind, String sourceName, float angleDeg, RwrSignal signal) {

    static final int MAX_NAME_LENGTH = 256;

    public static final StreamCodec<ByteBuf, RwrThreat> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RwrThreat::emitterId,
            RwrThreatKind.CODEC, RwrThreat::kind,
            Codecs.utf(MAX_NAME_LENGTH), RwrThreat::sourceName,
            ByteBufCodecs.FLOAT, RwrThreat::angleDeg,
            RwrSignal.CODEC, RwrThreat::signal,
            RwrThreat::new);

    /** Cuts a name to the length the codec accepts; null becomes empty. */
    public static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
