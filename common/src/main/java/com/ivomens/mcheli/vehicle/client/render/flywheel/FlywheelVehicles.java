package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.aircraft.AircraftEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The vehicles whose model a Flywheel visual currently draws. The visuals update it from Flywheel's
 * frame workers, the entity renderer reads it on the render thread. It holds no Flywheel classes, so
 * it can be asked whether Flywheel is installed or not.
 */
public final class FlywheelVehicles {

    private static final Set<AircraftEntity> DRAWN = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private FlywheelVehicles() {
    }

    public static boolean isDrawn(AircraftEntity vehicle) {
        return DRAWN.contains(vehicle);
    }

    static void setDrawn(AircraftEntity vehicle, boolean drawn) {
        if (drawn) {
            DRAWN.add(vehicle);
        } else {
            DRAWN.remove(vehicle);
        }
    }
}
