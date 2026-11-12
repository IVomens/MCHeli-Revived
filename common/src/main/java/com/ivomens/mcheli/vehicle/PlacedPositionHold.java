package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.vehicle.component.TickContext;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import com.ivomens.mcheli.vehicle.settings.CarriageRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * With {@code FixVehicleAtPlacedPoint}, an emplaced vehicle settles for a while after it is placed
 * and then stays where it is: it leaves any cart and is pulled back to its spot every tick.
 */
@TickOrder(phase = TickPhase.PHYSICS)
final class PlacedPositionHold implements VehicleComponent {

    /** Ticks after placement before the vehicle is pinned. */
    private static final int SETTLE_TICKS = 200;
    /** A client takes over the synced height this often, so it does not drift from the server. */
    private static final int CLIENT_HEIGHT_SYNC_INTERVAL = 4;

    private final Entity vehicle;
    private final Supplier<CarriageRules> carriage;
    private double x;
    private double y;
    private double z;

    PlacedPositionHold(Entity vehicle, Supplier<CarriageRules> carriage) {
        this.vehicle = vehicle;
        this.carriage = carriage;
    }

    @Override
    public void serverTick(TickContext ctx) {
        if (!settled()) {
            remember();
            return;
        }
        release();
        pull();
    }

    @Override
    public void clientTick(TickContext ctx) {
        if (!settled()) {
            remember();
            return;
        }
        release();
        if (this.vehicle.tickCount % CLIENT_HEIGHT_SYNC_INTERVAL == 0) {
            this.y = this.vehicle.getY();
        }
        pull();
    }

    private boolean settled() {
        return this.vehicle.tickCount >= SETTLE_TICKS && this.carriage.get().holdAtPlacedPoint();
    }

    private void remember() {
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY();
        this.z = this.vehicle.getZ();
    }

    private void release() {
        this.vehicle.stopRiding();
        this.vehicle.setDeltaMovement(Vec3.ZERO);
    }

    private void pull() {
        Entity v = this.vehicle;
        v.setPos((v.getX() + this.x) / 2.0, (v.getY() + this.y) / 2.0, (v.getZ() + this.z) / 2.0);
    }
}
