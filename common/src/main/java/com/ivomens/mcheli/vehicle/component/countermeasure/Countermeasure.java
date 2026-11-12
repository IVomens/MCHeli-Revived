package com.ivomens.mcheli.vehicle.component.countermeasure;


/**
 * Something a vehicle switches on against incoming missiles. Each one is started its own way (a flare
 * pattern, a burst length) but all of them run the same cycle: an active phase, then a cooldown until
 * they can be used again, both counted down once per tick on each side.
 */
public sealed interface Countermeasure permits FlareDispenser, ChaffDispenser, ActiveProtection, IronCurtain {

    void tick();

    /** Whether it is working against missiles right now. */
    boolean isActive();

    /** Whether it is used up and still waiting to be ready again. */
    boolean isRecharging();
}
