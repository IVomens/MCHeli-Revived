package com.ivomens.mcheli.aircraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The on/off inputs of a control tick. They travel as one bit mask, in the order of the components. */
public record ControlSwitches(boolean ejectSeat, boolean useWeapon, boolean switchSearchLight, boolean useBrake,
                              boolean switchGunnerStatus, boolean throttleUp, boolean throttleDown, boolean moveLeft,
                              boolean moveRight, boolean openGui, boolean reload, boolean useChaff, boolean useAPS,
                              boolean useECMJammer) {

    public static final StreamCodec<ByteBuf, ControlSwitches> CODEC =
            ByteBufCodecs.SHORT.map(mask -> fromMask(mask & 0xFFFF), switches -> (short) switches.mask());

    public int mask() {
        boolean[] bits = {ejectSeat, useWeapon, switchSearchLight, useBrake, switchGunnerStatus, throttleUp,
                throttleDown, moveLeft, moveRight, openGui, reload, useChaff, useAPS, useECMJammer};
        int mask = 0;
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static ControlSwitches fromMask(int mask) {
        return new ControlSwitches(bit(mask, 0), bit(mask, 1), bit(mask, 2), bit(mask, 3), bit(mask, 4), bit(mask, 5),
                bit(mask, 6), bit(mask, 7), bit(mask, 8), bit(mask, 9), bit(mask, 10), bit(mask, 11), bit(mask, 12),
                bit(mask, 13));
    }

    private static boolean bit(int mask, int index) {
        return (mask & 1 << index) != 0;
    }
}
