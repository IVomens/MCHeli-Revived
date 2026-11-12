package com.ivomens.mcheli.combat.weapon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the client asks for with a shot: the weapon mode it fires in, and the target its seeker locked.
 * Each weapon kind reads what it needs; a kind without modes ignores the mode, one without a seeker the
 * target.
 *
 * @param mode     the weapon's mode on the client (high explosive, top attack, TV guidance...)
 * @param targetId the entity id of the locked target, or {@link #NO_TARGET}
 */
public record FireRequest(int mode, int targetId) {

    /** Entity id 0 when nothing is locked. */
    public static final int NO_TARGET = 0;

    public static final FireRequest NONE = new FireRequest(0, NO_TARGET);

    public static final StreamCodec<ByteBuf, FireRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FireRequest::mode,
            ByteBufCodecs.INT, FireRequest::targetId,
            FireRequest::new);

    public FireRequest withMode(int newMode) {
        return new FireRequest(newMode, this.targetId);
    }

    public FireRequest withTarget(int newTargetId) {
        return new FireRequest(this.mode, newTargetId);
    }

    public static FireRequest inMode(int mode) {
        return new FireRequest(mode, NO_TARGET);
    }

    public static FireRequest locked(int targetId, int mode) {
        return new FireRequest(mode, targetId);
    }
}
