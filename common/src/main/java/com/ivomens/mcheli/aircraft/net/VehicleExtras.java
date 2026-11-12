package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Inputs only helicopters and ground vehicles have: the tow chain to release ({@code -1} for none)
 * and the rotor blade fold request.
 */
public record VehicleExtras(int unhitchChainId, VehicleExtras.BladeStatus blades) {

    public static final StreamCodec<ByteBuf, VehicleExtras> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VehicleExtras::unhitchChainId,
            Codecs.enumOf(BladeStatus.class), VehicleExtras::blades,
            VehicleExtras::new);

    /** No chain to release and no blade request: the extras of planes, ships and tanks. */
    public static final VehicleExtras NONE = new VehicleExtras(-1, BladeStatus.NONE);

    public enum BladeStatus {
        NONE,
        UNFOLD,
        FOLD
    }
}
