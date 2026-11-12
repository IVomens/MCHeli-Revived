package com.ivomens.mcheli.aircraft;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** A rack that carries another vehicle, for example a helicopter's cargo hook. */
public class SeatRackInfo extends SeatInfo {

    /** How close a vehicle must be to the entry position to be loaded. YAML {@code Racks[n].Range}. */
    public final float range;
    /** Names of the vehicles that fit this rack. YAML {@code Racks[n].Names}. */
    public final String[] names;

    public SeatRackInfo(String[] entityNames, double x, double y, double z, @Nullable Vec3 unmountPos,
                        VehicleParts.CameraPosition entryPos, float range, float yaw, float pitch,
                        boolean rotSeat) {
        super(new Vec3(x, y, z), unmountPos, false, entryPos, false, false, false, yaw, pitch, DEFAULT_MIN_PITCH,
                DEFAULT_MAX_PITCH, rotSeat);
        this.range = range;
        this.names = entityNames;
    }

    public SeatRackInfo(String[] entityNames, double x, double y, double z, VehicleParts.CameraPosition entryPos,
                        float range, float yaw, float pitch, boolean rotSeat) {
        this(entityNames, x, y, z, null, entryPos, range, yaw, pitch, rotSeat);
    }

    /** Where a vehicle has to be to be loaded onto the rack; stored as the rack's camera position. */
    public Vec3 getEntryPos() {
        return this.getCamPos().pos();
    }
}
