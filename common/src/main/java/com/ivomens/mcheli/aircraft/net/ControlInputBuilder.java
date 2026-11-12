package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.combat.weapon.FireRequest;
import net.minecraft.world.phys.Vec3;

/**
 * Collects one tick of crew input on the client, step by step as the key handlers run, and turns it
 * into the {@link ControlInput} that is sent. Fields start out as "no request".
 */
public class ControlInputBuilder {

    private ControlInput.UnmountAction unmount = ControlInput.UnmountAction.NONE;
    private ControlInput.VtolSwitch switchVtol = ControlInput.VtolSwitch.NONE;
    private ControlInput.ModeSwitch switchMode = ControlInput.ModeSwitch.NONE;
    private ControlInput.HatchSwitch switchHatch = ControlInput.HatchSwitch.NONE;
    private ControlInput.GearSwitch switchGear = ControlInput.GearSwitch.NONE;
    private ControlInput.RackAction putDownRack = ControlInput.RackAction.NONE;
    private ControlInput.CameraMode switchCameraMode = ControlInput.CameraMode.NORMAL;
    private int switches;
    private byte switchWeapon = -1;
    private byte useFlareType;
    private FireRequest fireRequest = FireRequest.NONE;
    private Vec3 useWeaponPos = Vec3.ZERO;
    private float useWeaponUserYaw;
    private float useWeaponUserPitch;
    private boolean detachedWeaponAim;
    private float detachedWeaponAimYaw;
    private float detachedWeaponAimPitch;
    private byte switchFreeLook;
    private byte switchRadar;

    public ControlInput build() {
        return new ControlInput(
                new ControlChanges(unmount, switchVtol, switchMode, switchHatch, switchGear, putDownRack),
                ControlSwitches.fromMask(switches),
                new WeaponSelection(switchWeapon, useFlareType),
                new WeaponUse(fireRequest, useWeaponPos, useWeaponUserYaw, useWeaponUserPitch),
                new DetachedAim(detachedWeaponAim, detachedWeaponAimYaw, detachedWeaponAimPitch),
                new ViewChanges(switchCameraMode, switchFreeLook, switchRadar));
    }

    // One-off requests

    public void setUnmount(ControlInput.UnmountAction unmount) {
        this.unmount = unmount;
    }

    public void setSwitchVtol(ControlInput.VtolSwitch switchVtol) {
        this.switchVtol = switchVtol;
    }

    public void setSwitchMode(ControlInput.ModeSwitch switchMode) {
        this.switchMode = switchMode;
    }

    public void setSwitchHatch(ControlInput.HatchSwitch switchHatch) {
        this.switchHatch = switchHatch;
    }

    public void setSwitchGear(ControlInput.GearSwitch switchGear) {
        this.switchGear = switchGear;
    }

    public void setPutDownRack(ControlInput.RackAction putDownRack) {
        this.putDownRack = putDownRack;
    }

    public void setSwitchCameraMode(ControlInput.CameraMode switchCameraMode) {
        this.switchCameraMode = switchCameraMode;
    }

    /** 0 for no change, 1 to turn free look on, 2 to turn it off. */
    public void setSwitchFreeLook(int switchFreeLook) {
        this.switchFreeLook = (byte) switchFreeLook;
    }

    public int switchFreeLook() {
        return this.switchFreeLook;
    }

    /** 0 for no change, 1 to turn the radar on, 2 to turn it off. */
    public void setSwitchRadar(int switchRadar) {
        this.switchRadar = (byte) switchRadar;
    }

    // Weapons

    /** The weapon to switch to; -1 for none. */
    public void setSwitchWeapon(int switchWeapon) {
        this.switchWeapon = (byte) switchWeapon;
    }

    public int switchWeapon() {
        return this.switchWeapon;
    }

    public void setUseFlareType(int useFlareType) {
        this.useFlareType = (byte) useFlareType;
    }

    public void setFireRequest(FireRequest request) {
        this.fireRequest = request;
    }

    public void setUseWeaponPos(double x, double y, double z) {
        this.useWeaponPos = new Vec3(x, y, z);
    }

    public void setUseWeaponUserYaw(float yaw) {
        this.useWeaponUserYaw = yaw;
    }

    public void setUseWeaponUserPitch(float pitch) {
        this.useWeaponUserPitch = pitch;
    }

    public void setDetachedWeaponAim(boolean detachedWeaponAim) {
        this.detachedWeaponAim = detachedWeaponAim;
    }

    public void setDetachedWeaponAimYaw(float yaw) {
        this.detachedWeaponAimYaw = yaw;
    }

    public void setDetachedWeaponAimPitch(float pitch) {
        this.detachedWeaponAimPitch = pitch;
    }

    // Keys held or pressed this tick, in the bit order of ControlSwitches

    public void setEjectSeat(boolean value) {
        setSwitch(0, value);
    }

    public void setUseWeapon(boolean value) {
        setSwitch(1, value);
    }

    public void setSwitchSearchLight(boolean value) {
        setSwitch(2, value);
    }

    public void setUseBrake(boolean value) {
        setSwitch(3, value);
    }

    public void setSwitchGunnerStatus(boolean value) {
        setSwitch(4, value);
    }

    public void setThrottleUp(boolean value) {
        setSwitch(5, value);
    }

    public void setThrottleDown(boolean value) {
        setSwitch(6, value);
    }

    public void setMoveLeft(boolean value) {
        setSwitch(7, value);
    }

    public void setMoveRight(boolean value) {
        setSwitch(8, value);
    }

    public void setOpenGui(boolean value) {
        setSwitch(9, value);
    }

    public void setReload(boolean value) {
        setSwitch(10, value);
    }

    public void setUseChaff(boolean value) {
        setSwitch(11, value);
    }

    public void setUseAPS(boolean value) {
        setSwitch(12, value);
    }

    public void setUseECMJammer(boolean value) {
        setSwitch(13, value);
    }

    private void setSwitch(int bit, boolean value) {
        this.switches = value ? this.switches | 1 << bit : this.switches & ~(1 << bit);
    }
}
