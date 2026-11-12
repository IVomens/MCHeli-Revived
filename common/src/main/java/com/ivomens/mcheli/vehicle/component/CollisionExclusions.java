package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.entityview.TowChain;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Entities the vehicle does not collide with: its crew, the vehicles on either end of its tow chain
 * and whatever carries it. The list is refreshed every half second; an entry outlives the
 * relationship by a few ticks, so a rider who just left does not get pushed out of the hull.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class CollisionExclusions implements VehicleComponent {

    private static final int REFRESH_INTERVAL = 10;
    private static final int CREW_TICKS = 8;
    private static final int LINKED_TICKS = 60;

    private final Entity vehicle;
    private final SeatsComponent seats;
    private final TowComponent tow;
    private final Map<Entity, Integer> ignored = new HashMap<>();

    public CollisionExclusions(Entity vehicle, SeatsComponent seats, TowComponent tow) {
        this.vehicle = vehicle;
        this.seats = seats;
        this.tow = tow;
    }

    public boolean ignores(Entity entity) {
        return this.ignored.containsKey(entity);
    }

    @Override
    public void serverTick(TickContext ctx) {
        if (ctx.age() % REFRESH_INTERVAL != 0) {
            return;
        }
        Entity ac = this.vehicle;
        for (int seatId = 0; seatId < 1 + this.seats.count(); seatId++) {
            Entity rider = this.seats.riderOf(seatId);
            if (rider != null) {
                this.ignored.put(rider, CREW_TICKS);
            }
        }
        TowChain towChain = this.tow.towChain();
        if (towChain != null && towChain.towed() != null) {
            this.ignored.put(towChain.towed(), LINKED_TICKS);
        }
        TowChain towedBy = this.tow.towedByChain();
        if (towedBy != null && towedBy.puller() != null) {
            this.ignored.put(towedBy.puller(), LINKED_TICKS);
        }
        if (ac.getVehicle() instanceof SeatEntity seat) {
            AircraftEntity carrier = seat.getParent();
            if (carrier != null) {
                this.ignored.put(carrier, LINKED_TICKS);
            }
        } else if (ac.getVehicle() != null) {
            this.ignored.put(ac.getVehicle(), LINKED_TICKS);
        }
        this.ignored.replaceAll((entity, ticks) -> ticks - 1);
        this.ignored.values().removeIf(ticks -> ticks <= 0);
    }
}
