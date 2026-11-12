package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * A taxi route of a base, as the base screen edits it. {@code parkingHeading} is -1 when the parking
 * marker's own heading applies.
 */
public record TaxiRouteData(String routeId, String parkingId, TaxiRunways runways, List<String> waypointIds,
                            List<String> arrivalWaypointIds, int parkingHeading) {

    public static final StreamCodec<ByteBuf, TaxiRouteData> CODEC = StreamCodec.composite(
            WingmanCodecs.ID, TaxiRouteData::routeId,
            WingmanCodecs.ID, TaxiRouteData::parkingId,
            TaxiRunways.CODEC, TaxiRouteData::runways,
            WingmanCodecs.WAYPOINT_IDS, TaxiRouteData::waypointIds,
            WingmanCodecs.WAYPOINT_IDS, TaxiRouteData::arrivalWaypointIds,
            ByteBufCodecs.INT, TaxiRouteData::parkingHeading,
            TaxiRouteData::new);

    public TaxiRouteData {
        waypointIds = List.copyOf(waypointIds);
        arrivalWaypointIds = List.copyOf(arrivalWaypointIds);
    }
}
