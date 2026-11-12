package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * Sends an aircraft on a mission order. With a known {@code departureRouteId} the wingman taxis out
 * along it first; otherwise it starts airborne. Only the owner or an operator may order a wingman.
 */
public record DispatchOrderMessage(UUID wingmanId, String departureRouteId, List<MissionType> missionTypes,
                                   List<String> weapons, StrikePlan strike, FlightPlan flight)
        implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, DispatchOrderMessage> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DispatchOrderMessage::wingmanId,
            WingmanCodecs.ID, DispatchOrderMessage::departureRouteId,
            WingmanCodecs.MISSION_TYPES, DispatchOrderMessage::missionTypes,
            WingmanCodecs.WEAPONS, DispatchOrderMessage::weapons,
            StrikePlan.CODEC, DispatchOrderMessage::strike,
            FlightPlan.CODEC, DispatchOrderMessage::flight,
            DispatchOrderMessage::new);

    public static final MessageType<DispatchOrderMessage> TYPE =
            MessageType.serverbound("wingman/dispatch_order", CODEC);

    public DispatchOrderMessage {
        missionTypes = List.copyOf(missionTypes);
        weapons = List.copyOf(weapons);
    }

    @Override
    public MessageType<DispatchOrderMessage> messageType() {
        return TYPE;
    }
}
