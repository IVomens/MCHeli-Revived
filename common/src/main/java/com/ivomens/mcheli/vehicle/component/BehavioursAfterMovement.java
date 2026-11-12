package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;

/**
 * Ticks the behaviours of other mods that run after the vehicle moved ({@link BehaviourTiming#AFTER_MOVEMENT}):
 * the last step that follows the hull, once hit boxes, parts and seats are where the physics put the vehicle.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL)
public final class BehavioursAfterMovement implements VehicleComponent {

    private final AddonBehaviours behaviours;

    public BehavioursAfterMovement(AddonBehaviours behaviours) {
        this.behaviours = behaviours;
    }

    @Override
    public void serverTick(TickContext ctx) {
        this.behaviours.tick(BehaviourTiming.AFTER_MOVEMENT);
    }

    @Override
    public void clientTick(TickContext ctx) {
        this.behaviours.tick(BehaviourTiming.AFTER_MOVEMENT);
    }
}
