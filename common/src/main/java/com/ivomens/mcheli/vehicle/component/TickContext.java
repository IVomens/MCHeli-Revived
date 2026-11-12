package com.ivomens.mcheli.vehicle.component;

/** What a component gets on each tick besides its own collaborators. Made once per vehicle and reused. */
public record TickContext(LifecycleComponent lifecycle) {

    /** Ticks the vehicle has run, as seen at the point of the pipeline where the component runs. */
    public int age() {
        return this.lifecycle.age();
    }
}
