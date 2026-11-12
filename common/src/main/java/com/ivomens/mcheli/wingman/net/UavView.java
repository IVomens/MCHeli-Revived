package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** An aircraft in the planner: its state and, on a mission, how far along the route it is. */
public record UavView(UUID id, String name, String state, int nodeIndex, int nodeCount) {

    public static final StreamCodec<ByteBuf, UavView> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, UavView::id,
            WingmanCodecs.NAME, UavView::name,
            WingmanCodecs.STATE, UavView::state,
            ByteBufCodecs.INT, UavView::nodeIndex,
            ByteBufCodecs.INT, UavView::nodeCount,
            UavView::new);
}
