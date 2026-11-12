package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Sets the sender's GPS target position, or deactivates it. */
public record GpsTargetMessage(Vec3 pos, boolean active) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, GpsTargetMessage> CODEC = StreamCodec.composite(
            Codecs.VEC3, GpsTargetMessage::pos,
            ByteBufCodecs.BOOL, GpsTargetMessage::active,
            GpsTargetMessage::new);

    public static final MessageType<GpsTargetMessage> TYPE = MessageType.serverbound("combat/gps_target", CODEC);

    @Override
    public MessageType<GpsTargetMessage> messageType() {
        return TYPE;
    }
}
