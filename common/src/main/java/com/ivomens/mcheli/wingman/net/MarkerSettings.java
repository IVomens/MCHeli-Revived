package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a wingman marker block is: its type, id, parent base and, for parking spots, the heading. */
public record MarkerSettings(MarkerType type, String id, String baseId, int parkingHeading) {

    public static final StreamCodec<ByteBuf, MarkerSettings> CODEC = StreamCodec.composite(
            WingmanCodecs.MARKER_TYPE, MarkerSettings::type,
            WingmanCodecs.ID, MarkerSettings::id,
            WingmanCodecs.ID, MarkerSettings::baseId,
            ByteBufCodecs.INT, MarkerSettings::parkingHeading,
            MarkerSettings::new);
}
