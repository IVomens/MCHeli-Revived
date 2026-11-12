package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Opens the base screen of a BASE marker: its taxi routes, its markers and the aircraft nearby. */
public record OpenBaseScreenMessage(BlockPos pos, String baseId, List<TaxiRouteData> routes, BaseMarkers markers,
                                    List<NearbyAircraft> nearbyAircraft) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, OpenBaseScreenMessage> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenBaseScreenMessage::pos,
            WingmanCodecs.ID, OpenBaseScreenMessage::baseId,
            WingmanCodecs.listOf(TaxiRouteData.CODEC), OpenBaseScreenMessage::routes,
            BaseMarkers.CODEC, OpenBaseScreenMessage::markers,
            WingmanCodecs.listOf(NearbyAircraft.CODEC), OpenBaseScreenMessage::nearbyAircraft,
            OpenBaseScreenMessage::new);

    public static final MessageType<OpenBaseScreenMessage> TYPE =
            MessageType.clientbound("wingman/open_base_screen", CODEC);

    public OpenBaseScreenMessage {
        routes = List.copyOf(routes);
        nearbyAircraft = List.copyOf(nearbyAircraft);
    }

    @Override
    public MessageType<OpenBaseScreenMessage> messageType() {
        return TYPE;
    }
}
