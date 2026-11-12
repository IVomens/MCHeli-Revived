package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.util.BoundedQueue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Where the vehicle was over the last half second. Crew dropping out is placed relative to where the
 * vehicle was then, and repelling needs it to have held still.
 */
public final class PositionHistory implements VehicleComponent {

    private static final int TICKS = 10;

    private final Entity vehicle;
    private final BoundedQueue<Vec3> positions = new BoundedQueue<>(TICKS, Vec3.ZERO);

    public PositionHistory(Entity vehicle) {
        this.vehicle = vehicle;
    }

    /** In the first ticks after spawning the history is the spawn point, not the origin. */
    public void startAtSpawn(int age) {
        if (age < 2) {
            this.positions.clear(this.vehicle.position());
        }
    }

    public void record() {
        this.positions.put(this.vehicle.position());
    }

    public Vec3 halfSecondAgo() {
        return this.positions.oldest();
    }
}
