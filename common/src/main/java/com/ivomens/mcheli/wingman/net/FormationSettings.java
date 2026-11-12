package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The global formation settings: where followers fly relative to the leader, how many may follow,
 * and the altitude band they attack in.
 */
public record FormationSettings(double sideDist, double altOffset, double rearDist, int maxWingmen,
                                double minAttackAltitude, double maxAttackAltitude) {

    public static final StreamCodec<ByteBuf, FormationSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, FormationSettings::sideDist,
            ByteBufCodecs.DOUBLE, FormationSettings::altOffset,
            ByteBufCodecs.DOUBLE, FormationSettings::rearDist,
            ByteBufCodecs.INT, FormationSettings::maxWingmen,
            ByteBufCodecs.DOUBLE, FormationSettings::minAttackAltitude,
            ByteBufCodecs.DOUBLE, FormationSettings::maxAttackAltitude,
            FormationSettings::new);

    /** False when any distance is NaN or infinite; such settings are ignored. */
    public boolean isFinite() {
        return Double.isFinite(sideDist) && Double.isFinite(altOffset) && Double.isFinite(rearDist)
                && Double.isFinite(minAttackAltitude) && Double.isFinite(maxAttackAltitude);
    }
}
