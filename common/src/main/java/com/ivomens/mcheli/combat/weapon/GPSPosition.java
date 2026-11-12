package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.weapon.net.GpsTargetMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** GPS target point a player entered for GPS guided weapons. */
public class GPSPosition {

    /** Server: the GPS point of each player, by entity id. */
    public static final Map<Integer, GPSPosition> currentGPSPositions = new HashMap<>();
    /** Client: the local player's GPS point. */
    public static final GPSPosition currentClientGPSPosition = new GPSPosition(0, 0, 0);

    private double x;
    private double y;
    private double z;
    private Entity owner;
    private boolean isActive;

    public boolean isActive() {
        return this.isActive;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void setOwner(Entity owner) {
        this.owner = owner;
    }

    public GPSPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Client: sets the local GPS point and sends it to the server. */
    public static void set(double x, double y, double z, boolean isActive, Entity owner) {
        if (!owner.level().isClientSide) {
            return;
        }
        currentClientGPSPosition.x = x;
        currentClientGPSPosition.y = y;
        currentClientGPSPosition.z = z;
        currentClientGPSPosition.isActive = isActive;
        currentClientGPSPosition.owner = owner;
        Network.sendToServer(new GpsTargetMessage(new Vec3(x, y, z), isActive));
    }
}
