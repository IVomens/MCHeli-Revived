package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One tick of crew input for the ground vehicle the sender rides. */
public record GroundVehicleControlMessage(ControlInput input, VehicleExtras extras) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, GroundVehicleControlMessage> CODEC = StreamCodec.composite(
            ControlInput.CODEC, GroundVehicleControlMessage::input,
            VehicleExtras.CODEC, GroundVehicleControlMessage::extras,
            GroundVehicleControlMessage::new);

    public static final MessageType<GroundVehicleControlMessage> TYPE =
            MessageType.serverbound("aircraft/ground_vehicle_control", CODEC);

    @Override
    public MessageType<GroundVehicleControlMessage> messageType() {
        return TYPE;
    }
}
