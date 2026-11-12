package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.CrewEjected;
import com.ivomens.mcheli.api.event.MissileWarned;
import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.api.event.VehicleEnteredWater;
import com.ivomens.mcheli.api.event.VehicleLanded;
import com.ivomens.mcheli.api.event.VehicleLeftWater;
import com.ivomens.mcheli.api.event.VehicleRearmed;
import com.ivomens.mcheli.api.event.VehicleRefueled;
import com.ivomens.mcheli.api.event.VehicleTookOff;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The API's events about what a vehicle does: taking off and landing, water, missile warnings, ejections,
 * rearming and refuelling. Like {@link ApiEvents}, an event nobody listens to is not built. The callers decide
 * the side (always the server); {@link VehicleContactWatch} raises the takeoff, landing and water events.
 */
public final class ActivityEvents {

    private ActivityEvents() {
    }

    static ApiEventBus bus() {
        return (ApiEventBus) McheliApi.get().events();
    }

    static void tookOff(Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleTookOff.class)) {
            bus.post(VehicleTookOff.class, new TookOff(vehicle));
        }
    }

    static void landed(Vehicle vehicle, boolean onWater) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleLanded.class)) {
            bus.post(VehicleLanded.class, new Landed(vehicle, onWater));
        }
    }

    static void enteredWater(Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleEnteredWater.class)) {
            bus.post(VehicleEnteredWater.class, new EnteredWater(vehicle));
        }
    }

    static void leftWater(Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleLeftWater.class)) {
            bus.post(VehicleLeftWater.class, new LeftWater(vehicle));
        }
    }

    /** Whether anyone listens to missile warnings, so the caller can skip collecting the missiles. */
    public static boolean listensToMissileWarnings() {
        return bus().hasListeners(MissileWarned.class);
    }

    public static void missileWarned(Vehicle vehicle, SeekerType seeker, List<? extends Entity> missiles) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(MissileWarned.class)) {
            bus.post(MissileWarned.class, new Warned(vehicle, seeker, List.copyOf(missiles)));
        }
    }

    public static void crewEjected(Vehicle vehicle, Entity rider, int seatId) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(CrewEjected.class)) {
            bus.post(CrewEjected.class, new Ejected(vehicle, rider, seatId));
        }
    }

    public static void rearmed(Vehicle vehicle, String weaponId, int rounds, @Nullable Entity supplier) {
        ApiEventBus bus = bus();
        if (rounds > 0 && bus.hasListeners(VehicleRearmed.class)) {
            bus.post(VehicleRearmed.class, new Rearmed(vehicle, weaponId, rounds, supplier));
        }
    }

    public static void refueled(Vehicle vehicle, int amount, @Nullable Entity supplier) {
        ApiEventBus bus = bus();
        if (amount > 0 && bus.hasListeners(VehicleRefueled.class)) {
            bus.post(VehicleRefueled.class, new Refueled(vehicle, amount, supplier));
        }
    }

    private record TookOff(Vehicle vehicle) implements VehicleTookOff {
    }

    private record Landed(Vehicle vehicle, boolean onWater) implements VehicleLanded {
    }

    private record EnteredWater(Vehicle vehicle) implements VehicleEnteredWater {
    }

    private record LeftWater(Vehicle vehicle) implements VehicleLeftWater {
    }

    private record Warned(Vehicle vehicle, SeekerType seeker, List<Entity> missiles) implements MissileWarned {
    }

    private record Ejected(Vehicle vehicle, Entity rider, int seatId) implements CrewEjected {
    }

    private record Rearmed(Vehicle vehicle, String weaponId, int rounds, @Nullable Entity supplier)
            implements VehicleRearmed {
    }

    private record Refueled(Vehicle vehicle, int amount, @Nullable Entity supplier) implements VehicleRefueled {
    }
}
