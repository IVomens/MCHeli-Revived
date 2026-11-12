package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Fires the launcher in the sender's hand at a locked target ({@code -1} for none), in a weapon mode,
 * from the eye position the client saw.
 */
public record LauncherFireMessage(int targetId, int mode, Vec3 pos) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, LauncherFireMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LauncherFireMessage::targetId,
            ByteBufCodecs.VAR_INT, LauncherFireMessage::mode,
            Codecs.VEC3, LauncherFireMessage::pos,
            LauncherFireMessage::new);

    public static final MessageType<LauncherFireMessage> TYPE = MessageType.serverbound("lweapon/fire", CODEC);

    @Override
    public MessageType<LauncherFireMessage> messageType() {
        return TYPE;
    }
}
