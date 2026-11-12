package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how the crew member fires this tick: what the shot asks for (mode, lock), the position the
 * client saw the vehicle at, and the user's aim.
 */
public record WeaponUse(FireRequest request, Vec3 pos, float userYaw, float userPitch) {

    public static final StreamCodec<ByteBuf, WeaponUse> CODEC = StreamCodec.composite(
            FireRequest.CODEC, WeaponUse::request,
            Codecs.VEC3, WeaponUse::pos,
            ByteBufCodecs.FLOAT, WeaponUse::userYaw,
            ByteBufCodecs.FLOAT, WeaponUse::userPitch,
            WeaponUse::new);

    /** Furthest a client-reported weapon origin may be from the aircraft (latency at full speed included). */
    private static final double MAX_POS_OFFSET = 128.0;

    /**
     * This use with anything a malformed or malicious client could abuse replaced: a position that is
     * not finite or too far from the aircraft becomes the aircraft's position, an aim that is not
     * finite becomes 0.
     */
    public WeaponUse sanitizedFor(Vec3 aircraftPos) {
        Vec3 offset = pos.subtract(aircraftPos);
        boolean posValid = Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z)
                && offset.lengthSqr() <= MAX_POS_OFFSET * MAX_POS_OFFSET;
        return new WeaponUse(request, posValid ? pos : aircraftPos, finiteOrZero(userYaw),
                finiteOrZero(userPitch));
    }

    static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
