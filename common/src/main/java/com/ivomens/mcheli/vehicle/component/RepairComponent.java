package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehiclePeers;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.util.EntityIds;

import java.util.List;

/** Repair vehicles ({@code repairOtherVehiclesRange} > 0) slowly fix every vehicle around them. */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class RepairComponent implements VehicleComponent {

    private static final int REPAIR_OTHERS_INTERVAL = 20;

    private final AircraftEntity vehicle;
    private final LifecycleComponent lifecycle;
    private final BodyComponent body;

    public RepairComponent(AircraftEntity vehicle, LifecycleComponent lifecycle, BodyComponent body) {
        this.vehicle = vehicle;
        this.lifecycle = lifecycle;
        this.body = body;
    }

    @Override
    public void serverTick(TickContext ctx) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        float range = info != null ? info.file().repairOtherVehicles().range() : 0.0F;
        if (range <= 0.0F || this.lifecycle.age() % REPAIR_OTHERS_INTERVAL != 0) {
            return;
        }
        List<AircraftEntity> nearby = ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.body.hullBox().inflate(range, range, range));
        for (AircraftEntity other : nearby) {
            if (EntityIds.same(ac, other)) {
                continue;
            }
            DamageComponent hull = VehiclePeers.hull(other);
            if (hull.hp() < hull.maxHp()) {
                hull.setDamageTaken(hull.damageTaken() - info.file().repairOtherVehicles().value());
            }
        }
    }
}
