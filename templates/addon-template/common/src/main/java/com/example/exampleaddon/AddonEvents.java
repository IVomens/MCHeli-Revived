package com.example.exampleaddon;

import com.ivomens.mcheli.api.event.EventBus;
import com.ivomens.mcheli.api.event.VehicleSpawned;
import com.ivomens.mcheli.api.event.WeaponFired;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The sample listeners: a log line for every vehicle that appears, and a count of the shots fired with the
 * addon's own weapon. MCHeli's events have their own small bus, the same on both loaders; listeners run on
 * the server thread. Replace these with your own.
 */
public final class AddonEvents {

    /** The id of the sample weapon, from mcheli_pack/assets/mcheli/weapons/exampleaddon_cannon.yml. */
    public static final String CANNON = AddonCommon.MOD_ID + "_cannon";
    private static final AtomicLong CANNON_SHOTS = new AtomicLong();

    private AddonEvents() {
    }

    static void register(EventBus events) {
        events.register(VehicleSpawned.class, AddonEvents::onVehicleSpawned);
        events.register(WeaponFired.class, AddonEvents::onWeaponFired);
    }

    /** The shots fired with {@link #CANNON} since the game started. */
    public static long cannonShots() {
        return CANNON_SHOTS.get();
    }

    private static void onVehicleSpawned(VehicleSpawned event) {
        AddonCommon.LOGGER.debug("MCHeli vehicle spawned: {}", event.vehicle());
    }

    private static void onWeaponFired(WeaponFired event) {
        if (CANNON.equals(event.weaponId())) {
            CANNON_SHOTS.incrementAndGet();
        }
    }
}
