package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Creates or replaces a taxi route of a base. Existing routes can only be changed by their owner or an operator. */
public record SaveTaxiRouteMessage(String baseId, TaxiRouteData route) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, SaveTaxiRouteMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.ID, SaveTaxiRouteMessage::baseId,
            TaxiRouteData.CODEC, SaveTaxiRouteMessage::route,
            SaveTaxiRouteMessage::new);

    public static final MessageType<SaveTaxiRouteMessage> TYPE =
            MessageType.serverbound("wingman/save_taxi_route", CODEC);

    @Override
    public MessageType<SaveTaxiRouteMessage> messageType() {
        return TYPE;
    }
}
