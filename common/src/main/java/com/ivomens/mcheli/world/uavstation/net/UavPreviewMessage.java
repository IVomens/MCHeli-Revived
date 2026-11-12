package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Heartbeat from an open UAV station screen naming the paired UAV the player previews, so the server
 * streams that UAV's terrain for the camera feed. Empty when the preview stops; the server also expires
 * the preview shortly after the heartbeats stop.
 */
public record UavPreviewMessage(Optional<UUID> uavId) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, UavPreviewMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), UavPreviewMessage::uavId,
            UavPreviewMessage::new);

    public static final MessageType<UavPreviewMessage> TYPE = MessageType.serverbound("wingman/uav_preview", CODEC);

    @Override
    public MessageType<UavPreviewMessage> messageType() {
        return TYPE;
    }
}
