package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Opens the mission planner ({@code /wingman gui}) with every aircraft, route and marker of the level. */
public record MissionPlannerMessage(List<UavView> uavs, List<MissionRouteView> routes, List<MarkerView> markers,
                                    Vec3 playerPos) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, MissionPlannerMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.listOf(UavView.CODEC), MissionPlannerMessage::uavs,
            WingmanCodecs.listOf(MissionRouteView.CODEC), MissionPlannerMessage::routes,
            WingmanCodecs.listOf(MarkerView.CODEC), MissionPlannerMessage::markers,
            Codecs.VEC3, MissionPlannerMessage::playerPos,
            MissionPlannerMessage::new);

    public static final MessageType<MissionPlannerMessage> TYPE =
            MessageType.clientbound("wingman/mission_planner", CODEC);

    public MissionPlannerMessage {
        uavs = List.copyOf(uavs);
        routes = List.copyOf(routes);
        markers = List.copyOf(markers);
    }

    @Override
    public MessageType<MissionPlannerMessage> messageType() {
        return TYPE;
    }
}
