package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.VehicleDamageTaken;
import com.ivomens.mcheli.api.event.VehicleDamaged;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * The API's damage events, {@link VehicleDamaged} before a hit and {@link VehicleDamageTaken} after it, handed to
 * the {@link ApiEventBus} by the vehicle's damage component. Like {@link ApiEvents}, an event nobody listens to
 * is not built, and the weapon of a hit is only looked up for a listener.
 */
public final class DamageEvents {

    private DamageEvents() {
    }

    private static ApiEventBus bus() {
        return (ApiEventBus) McheliApi.get().events();
    }

    /**
     * A hit is about to land on the vehicle.
     *
     * @param weaponOf the MCHeli weapon type of a damage source, or null; asked only when someone listens
     * @return the damage before armor to apply, or {@link Float#NaN} when a listener cancelled the hit
     */
    public static float vehicleDamaged(Vehicle vehicle, DamageSource source, float amount,
                                       Function<DamageSource, String> weaponOf) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehicleDamaged.class)) {
            return amount;
        }
        Damaged event = new Damaged(vehicle, source, weaponOf.apply(source), amount);
        bus.post(VehicleDamaged.class, event);
        return event.isCancelled() ? Float.NaN : event.amount();
    }

    /** A hit cost the vehicle hit points; {@code hp} is what is left. */
    public static void vehicleDamageTaken(Vehicle vehicle, DamageSource source, Function<DamageSource, String> weaponOf,
                                          float damage, int hp, int maxHp) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleDamageTaken.class)) {
            bus.post(VehicleDamageTaken.class, new Taken(vehicle, source, source.getEntity(), weaponOf.apply(source),
                    damage, hp, maxHp));
        }
    }

    private record Taken(Vehicle vehicle, DamageSource source, @Nullable Entity attacker, @Nullable String weaponId,
                         float damage, int hp, int maxHp) implements VehicleDamageTaken {
    }

    /** Listeners may change the amount or cancel the hit. */
    private static final class Damaged implements VehicleDamaged {

        private final Vehicle vehicle;
        private final DamageSource source;
        @Nullable
        private final String weaponId;
        private float amount;
        private boolean cancelled;

        Damaged(Vehicle vehicle, DamageSource source, @Nullable String weaponId, float amount) {
            this.vehicle = vehicle;
            this.source = source;
            this.weaponId = weaponId;
            this.amount = amount;
        }

        @Override
        public Vehicle vehicle() {
            return this.vehicle;
        }

        @Override
        public DamageSource source() {
            return this.source;
        }

        @Nullable
        @Override
        public Entity attacker() {
            return this.source.getEntity();
        }

        @Nullable
        @Override
        public Entity directEntity() {
            return this.source.getDirectEntity();
        }

        @Nullable
        @Override
        public String weaponId() {
            return this.weaponId;
        }

        @Override
        public float amount() {
            return this.amount;
        }

        @Override
        public void setAmount(float amount) {
            if (Float.isNaN(amount)) {
                throw new IllegalArgumentException("Damage must be a number");
            }
            this.amount = Math.max(amount, 0.0F);
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
