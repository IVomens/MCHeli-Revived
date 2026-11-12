package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Plays a sound event, by id, at a position; {@code distort} randomises the pitch a little. */
public record SoundMessage(float x, float y, float z, String sound, boolean distort) implements ClientboundMessage {

    private static final int MAX_SOUND_LENGTH = 256;

    private static final StreamCodec<ByteBuf, SoundMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SoundMessage::x,
            ByteBufCodecs.FLOAT, SoundMessage::y,
            ByteBufCodecs.FLOAT, SoundMessage::z,
            Codecs.utf(MAX_SOUND_LENGTH), SoundMessage::sound,
            ByteBufCodecs.BOOL, SoundMessage::distort,
            SoundMessage::new);

    public static final MessageType<SoundMessage> TYPE = MessageType.clientbound("combat/sound", CODEC);

    public static SoundMessage at(Vec3 pos, String sound, boolean distort) {
        return new SoundMessage((float) pos.x, (float) pos.y, (float) pos.z, sound, distort);
    }

    @Override
    public MessageType<SoundMessage> messageType() {
        return TYPE;
    }
}
