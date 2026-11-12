package com.ivomens.mcheli.vehicle.component;

import javax.annotation.Nullable;

/**
 * One step of a vehicle's tick as the vehicle inspector lists it: its name, the phase it runs in and, for a
 * component's own tick, that component.
 */
public record ScheduledStep(String name, TickPhase phase, @Nullable VehicleComponent owner) {
}
