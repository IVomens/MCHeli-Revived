package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Reloads the definition of the sender's aircraft type for every aircraft of that type (development aid). */
public record ReloadVehicleContentMessage() implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ReloadVehicleContentMessage> CODEC =
            StreamCodec.unit(new ReloadVehicleContentMessage());

    public static final MessageType<ReloadVehicleContentMessage> TYPE =
            MessageType.serverbound("aircraft/reload_vehicle_content", CODEC);

    @Override
    public MessageType<ReloadVehicleContentMessage> messageType() {
        return TYPE;
    }
}
