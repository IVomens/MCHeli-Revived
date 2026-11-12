package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;

/**
 * A vehicle started one of its countermeasures, at the crew's command or an AI gunner's. Fired only when it
 * actually starts, not when it is still recharging.
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface CountermeasureDeployed extends McheliEvent {

    Vehicle vehicle();

    CountermeasureType type();
}
