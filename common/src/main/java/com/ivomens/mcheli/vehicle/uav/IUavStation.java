package com.ivomens.mcheli.vehicle.uav;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.entityview.RemoteStation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Anything an operator can sit at to fly a UAV. The extended tracking range and chunk streaming around a
 * controlled UAV key off {@link #getOperator()} and {@link #getControlled()}, see {@link UavControl}.
 */
public interface IUavStation extends RemoteStation {

    Vec3 getPos();

    @Override
    @Nullable
    AircraftEntity getControlled();

    void setControlled(@Nullable AircraftEntity aircraft);

    @NotNull StationType getStationType();

    default void setType(StationType type) {
    }

    default void setUavPosition(double x, double y, double z) {
    }

    /** Where the station launches its UAVs: its position plus the launch offset set on its screen. */
    Vec3 launchPoint();

    /** The player currently seated at the station, if any. */
    @Nullable
    Entity getOperator();

    enum StationType {
        DEFAULT,
        SMALL
    }
}
