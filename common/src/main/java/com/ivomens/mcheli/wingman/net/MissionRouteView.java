package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** A saved mission route and its nodes in text form. */
public record MissionRouteView(String name, List<String> nodes) {

    public static final StreamCodec<ByteBuf, MissionRouteView> CODEC = StreamCodec.composite(
            WingmanCodecs.NAME, MissionRouteView::name,
            WingmanCodecs.MISSION_NODES, MissionRouteView::nodes,
            MissionRouteView::new);

    public MissionRouteView {
        nodes = List.copyOf(nodes);
    }
}
