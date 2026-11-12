package com.ivomens.mcheli.combat.weapon;

/**
 * Where a group's barrels point relative to the platform, as the vehicle's aiming sets them: yaw and
 * pitch with their values of the last tick, the yaw they rest at, and the yaw of the turret they sit on.
 */
public final class TurretAngles {

    private float yaw;
    private float prevYaw;
    private float defaultYaw;
    private float turretYaw;
    private float pitch;
    private float prevPitch;

    public float yaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float prevYaw() {
        return this.prevYaw;
    }

    public void setPrevYaw(float prevYaw) {
        this.prevYaw = prevYaw;
    }

    public float defaultYaw() {
        return this.defaultYaw;
    }

    public void setDefaultYaw(float defaultYaw) {
        this.defaultYaw = defaultYaw;
    }

    public float turretYaw() {
        return this.turretYaw;
    }

    public void setTurretYaw(float turretYaw) {
        this.turretYaw = turretYaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float prevPitch() {
        return this.prevPitch;
    }

    public void setPrevPitch(float prevPitch) {
        this.prevPitch = prevPitch;
    }

    void rememberPrevious() {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }
}
