package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Deletes a taxi route; only its owner or an operator may. */
public record DeleteTaxiRouteMessage(String routeId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, DeleteTaxiRouteMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.ID, DeleteTaxiRouteMessage::routeId,
            DeleteTaxiRouteMessage::new);

    public static final MessageType<DeleteTaxiRouteMessage> TYPE =
            MessageType.serverbound("wingman/delete_taxi_route", CODEC);

    @Override
    public MessageType<DeleteTaxiRouteMessage> messageType() {
        return TYPE;
    }
}
