package com.ivomens.mcheli.aircraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One tick of crew input for an aircraft or vehicle, as the client sends it with every control
 * message. The client collects it in a {@link ControlInputBuilder}.
 *
 * @param changes   one-off requests: unmounting, VTOL, gunner and hover modes, hatch, gear, rack
 * @param switches  the keys held or pressed this tick
 * @param selection weapon switch and flare requests
 * @param weaponUse where and how a weapon is fired, when {@code switches.useWeapon()}
 * @param aim       the pilot's detached turret aim
 * @param view      camera mode, free look and radar requests
 */
public record ControlInput(ControlChanges changes, ControlSwitches switches, WeaponSelection selection,
                           WeaponUse weaponUse, DetachedAim aim, ViewChanges view) {

    public static final StreamCodec<ByteBuf, ControlInput> CODEC = StreamCodec.composite(
            ControlChanges.CODEC, ControlInput::changes,
            ControlSwitches.CODEC, ControlInput::switches,
            WeaponSelection.CODEC, ControlInput::selection,
            WeaponUse.CODEC, ControlInput::weaponUse,
            DetachedAim.CODEC, ControlInput::aim,
            ViewChanges.CODEC, ControlInput::view,
            ControlInput::new);

    public enum UnmountAction {
        NONE,
        UNMOUNT_SELF,
        UNMOUNT_CREW,
        UNMOUNT_AIRCRAFT
    }

    public enum VtolSwitch {
        NONE,
        VTOL_OFF,
        VTOL_ON
    }

    public enum ModeSwitch {
        NONE,
        GUNNER_OFF,
        GUNNER_ON,
        HOVERING_OFF,
        HOVERING_ON
    }

    public enum HatchSwitch {
        NONE,
        FOLD,
        UNFOLD
    }

    public enum RackAction {
        NONE,
        MOUNT,
        UNMOUNT,
        RIDE
    }

    public enum GearSwitch {
        NONE,
        FOLD,
        UNFOLD
    }

    /** The order matters: the server switches to the camera mode with this ordinal. */
    public enum CameraMode {
        NORMAL,
        NIGHT_VIS,
        THERMAL_VIS
    }
}
