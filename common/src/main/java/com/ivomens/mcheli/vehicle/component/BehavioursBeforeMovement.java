package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;

/**
 * Ticks the behaviours of other mods that run before the vehicle moves ({@link BehaviourTiming#BEFORE_MOVEMENT}):
 * the last step of the systems, right before the vehicle type's controls and physics.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class BehavioursBeforeMovement implements VehicleComponent {

    private final AddonBehaviours behaviours;

    public BehavioursBeforeMovement(AddonBehaviours behaviours) {
        this.behaviours = behaviours;
    }

    @Override
    public void serverTick(TickContext ctx) {
        this.behaviours.tick(BehaviourTiming.BEFORE_MOVEMENT);
    }

    @Override
    public void clientTick(TickContext ctx) {
        this.behaviours.tick(BehaviourTiming.BEFORE_MOVEMENT);
    }

    @Override
    public void describe(StateSink state) {
        this.behaviours.describe(state);
    }
}
