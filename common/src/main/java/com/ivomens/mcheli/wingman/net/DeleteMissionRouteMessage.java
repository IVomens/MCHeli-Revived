package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Deletes a saved mission route; only its creator or an operator may. */
public record DeleteMissionRouteMessage(String name) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, DeleteMissionRouteMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.NAME, DeleteMissionRouteMessage::name,
            DeleteMissionRouteMessage::new);

    public static final MessageType<DeleteMissionRouteMessage> TYPE =
            MessageType.serverbound("wingman/delete_mission_route", CODEC);

    @Override
    public MessageType<DeleteMissionRouteMessage> messageType() {
        return TYPE;
    }
}
