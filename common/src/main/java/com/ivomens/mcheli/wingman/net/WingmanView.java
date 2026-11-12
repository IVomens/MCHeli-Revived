package com.ivomens.mcheli.wingman.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * A wingman following the player's aircraft. {@code attackMode} is a code of
 * {@link com.ivomens.mcheli.core.wingman.brain.AttackMode}; {@code weaponType} is empty when any weapon may be used.
 */
public record WingmanView(UUID id, String name, int slot, String state, int attackMode, String weaponType) {

    public static final StreamCodec<ByteBuf, WingmanView> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingmanView::id,
            WingmanCodecs.NAME, WingmanView::name,
            ByteBufCodecs.INT, WingmanView::slot,
            WingmanCodecs.STATE, WingmanView::state,
            ByteBufCodecs.INT, WingmanView::attackMode,
            WingmanCodecs.STATE, WingmanView::weaponType,
            WingmanView::new);

    public WingmanView withAttackMode(int mode) {
        return new WingmanView(this.id, this.name, this.slot, this.state, mode, this.weaponType);
    }
}
