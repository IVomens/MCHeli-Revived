package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A vehicle as its weapons see it: which weapon a crew member has selected, and the camera through which its
 * crew watches a TV missile. Every vehicle of the core implements it; the weapons never name the core's
 * classes.
 */
public interface ArmedVehicle extends Vehicle {

    /** The weapon each crew member has selected. */
    WeaponSelection weapons();

    /** The camera through which the crew watches a TV missile. */
    MissileCamera cameras();

    /** Destroys the vehicle, as a UAV's self-destruct charge does. */
    void destruct();
}
