package com.ivomens.mcheli.vehicle.component;

/**
 * Whether a parked vehicle is resting, and how long it has stood still. Anything that can change the
 * vehicle (power, a passenger, new motion from the server) wakes it through here; {@link RestComponent}
 * decides when it may rest again.
 * <p>
 * It is its own component so that the many components that wake the vehicle do not depend on
 * everything {@link RestComponent} looks at to decide.
 */
public final class RestState implements VehicleComponent {

    private int stillTicks;
    private boolean resting;

    public boolean isResting() {
        return this.resting;
    }

    public void wake() {
        this.resting = false;
        this.stillTicks = 0;
    }

    /** End of an ordinary tick: the vehicle rests once it has been still for {@code settleTicks} ticks. */
    void countStillTick(boolean still, int settleTicks) {
        if (!still) {
            this.stillTicks = 0;
            return;
        }
        if (++this.stillTicks >= settleTicks) {
            this.resting = true;
        }
    }
}
