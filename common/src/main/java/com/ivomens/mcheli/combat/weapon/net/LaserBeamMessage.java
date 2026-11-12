package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a laser beam, with impact sparks when it hit something. Purely cosmetic: the damage is dealt
 * on the server by {@link com.ivomens.mcheli.weapon.LaserWeapon}.
 */
public record LaserBeamMessage(Vec3 source, Vec3 target, LaserBeamStyle style, boolean hitSomething)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, LaserBeamMessage> CODEC = StreamCodec.composite(
            Codecs.VEC3, LaserBeamMessage::source,
            Codecs.VEC3, LaserBeamMessage::target,
            LaserBeamStyle.CODEC, LaserBeamMessage::style,
            ByteBufCodecs.BOOL, LaserBeamMessage::hitSomething,
            LaserBeamMessage::new);

    public static final MessageType<LaserBeamMessage> TYPE = MessageType.clientbound("combat/laser_beam", CODEC);

    @Override
    public MessageType<LaserBeamMessage> messageType() {
        return TYPE;
    }
}
