package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.util.RandomSource;

/**
 * How a burning wreck tumbles on the clients, with the tumble rates {@link WreckComponent} picks when
 * the vehicle is destroyed. The vehicle type picks the motion; ground vehicles stay put.
 */
public interface WreckMotion {

    /** Stays where it is. */
    WreckMotion STILL = (wreck, vehicle) -> {
    };

    /** Adjusts the picked tumble once the crew is out; runs on both sides. */
    default void adjustTumble(WreckComponent wreck, AircraftEntity vehicle, RandomSource random) {
    }

    /** Client, every tick while the wreck burns. */
    void tick(WreckComponent wreck, AircraftEntity vehicle);
}
