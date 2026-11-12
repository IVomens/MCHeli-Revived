package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Camera and sensor requests: a camera mode other than {@code NORMAL} switches to it; free look and
 * radar are 0 for no change, 1 for on and 2 for off.
 */
public record ViewChanges(ControlInput.CameraMode camera, byte freeLook, byte radar) {

    public static final StreamCodec<ByteBuf, ViewChanges> CODEC = StreamCodec.composite(
            Codecs.enumOf(ControlInput.CameraMode.class), ViewChanges::camera,
            ByteBufCodecs.BYTE, ViewChanges::freeLook,
            ByteBufCodecs.BYTE, ViewChanges::radar,
            ViewChanges::new);
}
