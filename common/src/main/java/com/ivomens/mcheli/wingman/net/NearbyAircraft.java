package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** An aircraft the player could take as a wingman or send on an order. */
public record NearbyAircraft(UUID id, String name) {

    public static final StreamCodec<ByteBuf, NearbyAircraft> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NearbyAircraft::id,
            WingmanCodecs.NAME, NearbyAircraft::name,
            NearbyAircraft::new);
}
