package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** The markers of one base, by type. Waypoints include unparented ones placed nearby. */
public record BaseMarkers(List<MarkerView> parking, List<MarkerView> waypoints, List<MarkerView> runwayA,
                          List<MarkerView> runwayB, List<MarkerView> helipads, List<MarkerView> helipadB) {

    public static final StreamCodec<ByteBuf, BaseMarkers> CODEC = StreamCodec.composite(
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::parking,
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::waypoints,
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::runwayA,
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::runwayB,
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::helipads,
            WingmanCodecs.listOf(MarkerView.CODEC), BaseMarkers::helipadB,
            BaseMarkers::new);

    public BaseMarkers {
        parking = List.copyOf(parking);
        waypoints = List.copyOf(waypoints);
        runwayA = List.copyOf(runwayA);
        runwayB = List.copyOf(runwayB);
        helipads = List.copyOf(helipads);
        helipadB = List.copyOf(helipadB);
    }
}
