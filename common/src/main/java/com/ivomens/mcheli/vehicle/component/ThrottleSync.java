package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.core.sim.propulsion.ThrottleModel;

/**
 * The throttle as the server publishes it to clients, in its 0.05 sync steps. The engine writes it;
 * clients follow it, and the fuel burns by it.
 * <p>
 * It is its own component so that what reads the published throttle does not depend on the engine,
 * which itself depends on the fuel.
 */
public final class ThrottleSync implements VehicleComponent {

    private final SyncedValue<Integer> steps;

    public ThrottleSync(SyncedValue<Integer> steps) {
        this.steps = steps;
    }

    /** The throttle as the server last published it. */
    public double get() {
        return ThrottleModel.fromSyncSteps(this.steps.get());
    }

    /** Server: tells clients the throttle. */
    public void publish(double throttle) {
        this.steps.set(ThrottleModel.toSyncSteps(throttle));
    }
}
