package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One-off requests of a control tick; {@code NONE} asks for nothing. */
public record ControlChanges(ControlInput.UnmountAction unmount, ControlInput.VtolSwitch vtol,
                             ControlInput.ModeSwitch mode, ControlInput.HatchSwitch hatch, ControlInput.GearSwitch gear,
                             ControlInput.RackAction rack) {

    public static final StreamCodec<ByteBuf, ControlChanges> CODEC = StreamCodec.composite(
            Codecs.enumOf(ControlInput.UnmountAction.class), ControlChanges::unmount,
            Codecs.enumOf(ControlInput.VtolSwitch.class), ControlChanges::vtol,
            Codecs.enumOf(ControlInput.ModeSwitch.class), ControlChanges::mode,
            Codecs.enumOf(ControlInput.HatchSwitch.class), ControlChanges::hatch,
            Codecs.enumOf(ControlInput.GearSwitch.class), ControlChanges::gear,
            Codecs.enumOf(ControlInput.RackAction.class), ControlChanges::rack,
            ControlChanges::new);
}
