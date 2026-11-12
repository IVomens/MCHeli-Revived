package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** A wingman marker as the planner screens show it. */
public record MarkerView(MarkerType type, String id, BlockPos pos) {

    public static final StreamCodec<ByteBuf, MarkerView> CODEC = StreamCodec.composite(
            WingmanCodecs.MARKER_TYPE, MarkerView::type,
            WingmanCodecs.ID, MarkerView::id,
            BlockPos.STREAM_CODEC, MarkerView::pos,
            MarkerView::new);
}
