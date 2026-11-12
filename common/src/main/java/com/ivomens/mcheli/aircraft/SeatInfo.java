package com.ivomens.mcheli.aircraft;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** A seat of a vehicle. Racks, which carry other vehicles instead of riders, extend it. */
public class SeatInfo {

    static final float DEFAULT_MIN_PITCH = -30.0F;
    static final float DEFAULT_MAX_PITCH = 70.0F;

    /** YAML {@code Seats[n].Position}. */
    public final Vec3 pos;
    /** Where the rider is put when leaving, or null for the default. YAML {@code Seats[n].UnmountPos}. */
    public final Vec3 unmountPos;
    /** YAML {@code Seats[n].Gunner}. */
    public final boolean gunner;
    /** Whether the rider can switch to gunner view. YAML {@code Seats[n].SwitchGunner}. */
    public final boolean switchgunner;
    /** Whether the view is locked to {@link #fixYaw}/{@link #fixPitch}. YAML {@code Seats[n].FixRot}. */
    public final boolean fixRot;
    /** YAML {@code Seats[n].FixYaw}. */
    public final float fixYaw;
    /** YAML {@code Seats[n].FixPitch}. */
    public final float fixPitch;
    /** YAML {@code Seats[n].MinPitch}, default -30. */
    public final float minPitch;
    /** YAML {@code Seats[n].MaxPitch}, default 70. */
    public final float maxPitch;
    /** YAML {@code Seats[n].RotSeat}. */
    public final boolean rotSeat;
    /** YAML {@code Seats[n].Camera}. */
    private final VehicleParts.CameraPosition camPos;
    /** YAML {@code Seats[n].InvCamPos}. */
    public final boolean invCamPos;

    public SeatInfo(Vec3 pos, @Nullable Vec3 unmountPos, boolean gunner, VehicleParts.CameraPosition camPos,
                    boolean invCamPos, boolean switchGunner, boolean fixRot, float fixYaw, float fixPitch,
                    float minPitch, float maxPitch, boolean rotSeat) {
        this.camPos = camPos;
        this.pos = pos;
        this.unmountPos = unmountPos;
        this.gunner = gunner;
        this.invCamPos = invCamPos;
        this.switchgunner = switchGunner;
        this.fixRot = fixRot;
        this.fixYaw = fixYaw;
        this.fixPitch = fixPitch;
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.rotSeat = rotSeat;
    }

    public SeatInfo(Vec3 pos, boolean gunner, VehicleParts.CameraPosition camPos, boolean invCamPos,
                    boolean switchGunner, boolean fixRot, float fixYaw, float fixPitch, float minPitch,
                    float maxPitch, boolean rotSeat) {
        this(pos, null, gunner, camPos, invCamPos, switchGunner, fixRot, fixYaw, fixPitch, minPitch, maxPitch,
                rotSeat);
    }

    public SeatInfo(Vec3 pos, boolean gunner, VehicleParts.CameraPosition camPos, boolean invCamPos,
                    boolean switchGunner, boolean fixRot, float fixYaw, float fixPitch, boolean rotSeat) {
        this(pos, null, gunner, camPos, invCamPos, switchGunner, fixRot, fixYaw, fixPitch, DEFAULT_MIN_PITCH,
                DEFAULT_MAX_PITCH, rotSeat);
    }

    public SeatInfo(Vec3 pos, VehicleParts.CameraPosition camPos, float fixYaw, float fixPitch, boolean rotSeat) {
        this(pos, null, false, camPos, false, false, false, fixYaw, fixPitch, DEFAULT_MIN_PITCH, DEFAULT_MAX_PITCH,
                rotSeat);
    }

    public SeatInfo(Vec3 pos, boolean rotSeat) {
        this(pos, null, false, null, false, false, false, 0.0F, 0.0F, DEFAULT_MIN_PITCH, DEFAULT_MAX_PITCH, rotSeat);
    }

    public VehicleParts.CameraPosition getCamPos() {
        return this.camPos;
    }

    @Override
    public String toString() {
        return "SeatInfo{" +
                "pos=" + pos +
                ", gunner=" + gunner +
                ", switchgunner=" + switchgunner +
                ", fixRot=" + fixRot +
                ", fixYaw=" + fixYaw +
                ", fixPitch=" + fixPitch +
                ", minPitch=" + minPitch +
                ", maxPitch=" + maxPitch +
                ", rotSeat=" + rotSeat +
                ", camPos=" + camPos +
                ", invCamPos=" + invCamPos +
                '}';
    }
}
