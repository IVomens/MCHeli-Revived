package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How an order is flown: the home base, the base a ferry flight ends at, the arrival route, cruise
 * altitude, time limit, and whether to take off and land vertically.
 */
public record FlightPlan(String baseId, String ferryDestBase, String arrivalRouteId, double cruiseAlt,
                         int timeLimitSeconds, boolean useVstol) {

    public static final StreamCodec<ByteBuf, FlightPlan> CODEC = StreamCodec.composite(
            WingmanCodecs.ID, FlightPlan::baseId,
            WingmanCodecs.ID, FlightPlan::ferryDestBase,
            WingmanCodecs.ID, FlightPlan::arrivalRouteId,
            ByteBufCodecs.DOUBLE, FlightPlan::cruiseAlt,
            ByteBufCodecs.INT, FlightPlan::timeLimitSeconds,
            ByteBufCodecs.BOOL, FlightPlan::useVstol,
            FlightPlan::new);
}
