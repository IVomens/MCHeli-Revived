package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Creates or replaces a saved mission route. Existing routes belong to their creator. */
public record SaveMissionRouteMessage(String name, List<String> nodes) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, SaveMissionRouteMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.NAME, SaveMissionRouteMessage::name,
            WingmanCodecs.MISSION_NODES, SaveMissionRouteMessage::nodes,
            SaveMissionRouteMessage::new);

    public static final MessageType<SaveMissionRouteMessage> TYPE =
            MessageType.serverbound("wingman/save_mission_route", CODEC);

    public SaveMissionRouteMessage {
        nodes = List.copyOf(nodes);
    }

    @Override
    public MessageType<SaveMissionRouteMessage> messageType() {
        return TYPE;
    }
}
