package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * A vehicle's missile warning went off: missiles are homing on it or its crew, and its crew hears the alert
 * (for a UAV, its operator). Fired with every alert, about once a second while the threat lasts, by the same
 * warning the crew hears: only vehicles with flares or chaff have one, it is quiet while they are dispensing
 * flares or chaff, and it needs someone aboard. A missile of another mod that reports it tracks the vehicle
 * (MCHeli's {@code TRACKED} countermeasure flag) sets it off too.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface MissileWarned extends McheliEvent {

    Vehicle vehicle();

    /** What the incoming missiles seek with, as the warning tells the crew. */
    SeekerType seeker();

    /**
     * The MCHeli missiles within 60 blocks homing on the vehicle or its crew, in no particular order; empty when
     * only another mod's missile set the warning off. An unmodifiable list.
     */
    List<Entity> missiles();
}
