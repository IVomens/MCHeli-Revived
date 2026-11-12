package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** The runways of a taxi route: departure, optional second departure runway, and arrival. */
public record TaxiRunways(String runwayId, String runwayBId, String arrivalRunwayId) {

    public static final StreamCodec<ByteBuf, TaxiRunways> CODEC = StreamCodec.composite(
            WingmanCodecs.ID, TaxiRunways::runwayId,
            WingmanCodecs.ID, TaxiRunways::runwayBId,
            WingmanCodecs.ID, TaxiRunways::arrivalRunwayId,
            TaxiRunways::new);
}
