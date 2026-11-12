package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Switches the camera mode of the laser designator the sender operates. */
public record GltdCameraModeMessage(int mode) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, GltdCameraModeMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GltdCameraModeMessage::mode,
            GltdCameraModeMessage::new);

    public static final MessageType<GltdCameraModeMessage> TYPE = MessageType.serverbound("gltd/camera_mode", CODEC);

    @Override
    public MessageType<GltdCameraModeMessage> messageType() {
        return TYPE;
    }
}
