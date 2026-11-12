package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.VehiclePeers;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.event.ActivityEvents;
import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The fuel tank: what is in it (synced, so the HUD can show it), burning fuel with the throttle,
 * refilling from the inventory, and tankers filling up the vehicles around them. Electric vehicles
 * keep their energy in the same amount.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class FuelComponent implements VehicleComponent {

    private static final int CONSUMPTION_INTERVAL = 20;
    private static final int TANKER_SUPPLY_INTERVAL = 10;
    private static final int TANKER_SUPPLY_AMOUNT = 30;
    /** A refuelled vehicle burns nothing for this long, so a tanker can actually fill it up. */
    private static final int SUPPLIED_GRACE_TICKS = 40;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SyncedValue<Integer> amount;
    private final SyncedValue<String> fuelType;
    private final ThrottleSync throttle;
    private final LifecycleComponent lifecycle;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final FuelRefill refill;
    private final FuelTankAdapter tank;
    /** Fractional fuel burnt but not yet taken from the tank. */
    private double pendingConsumption;
    private int suppliedGraceTicks;

    public FuelComponent(AircraftEntity vehicle, StatusWord status, SyncedValue<Integer> amount,
            SyncedValue<String> fuelType, ThrottleSync throttle, LifecycleComponent lifecycle, SeatsComponent seats,
            BodyComponent body, AircraftInventory cargo) {
        this.vehicle = vehicle;
        this.status = status;
        this.amount = amount;
        this.fuelType = fuelType;
        this.throttle = throttle;
        this.lifecycle = lifecycle;
        this.seats = seats;
        this.body = body;
        this.refill = new FuelRefill(vehicle, this, lifecycle, cargo);
        this.tank = new FuelTankAdapter(vehicle, this);
    }

    public int amount() {
        return this.amount.get();
    }

    /** Sets the amount, clamped to the capacity; only the server writes it. */
    public void setAmount(int amount) {
        amount = Mth.clamp(amount, 0, Math.max(capacity(), 0));
        if (amount != amount()) {
            this.amount.set(amount);
        }
    }

    /**
     * Server: fills the tank up to {@code amount} from a refuelling source, and tells the API's listeners how
     * much went in.
     *
     * @param supplier the tanker vehicle, or null for the fuel slot, pipes and cables
     */
    void refuel(int amount, @Nullable Entity supplier) {
        int before = amount();
        setAmount(amount);
        ActivityEvents.refueled(this.vehicle, amount() - before, supplier);
    }

    /**
     * Whether there is fuel to burn: a vehicle without a tank, fuel left, or infinite fuel for the
     * pilot (with {@code checkOtherSeats}, for anyone aboard).
     */
    public boolean hasUsable(boolean checkOtherSeats) {
        return capacity() <= 0 || amount() > 1 || isInfinite(this.vehicle.getRiddenByEntity(), checkOtherSeats);
    }

    /** Creative crew and the server's infinite fuel setting never run dry. */
    public boolean isInfinite(@Nullable Entity rider, boolean checkOtherSeats) {
        if (Creative.isCreative(rider) || this.status.get(StatusWord.INFINITE_FUEL)) {
            return true;
        }
        if (checkOtherSeats) {
            for (SeatEntity seat : this.seats.all()) {
                if (seat != null && Creative.isCreative(seat.getRiddenByEntity())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Electric vehicles keep their energy in the fuel amount; returns how much was accepted. */
    public int charge(int energy, boolean simulate) {
        if (!this.amount.isWritable() || energy <= 0) {
            return 0;
        }
        int accepted = Math.min(energy, capacity() - amount());
        if (accepted > 0 && !simulate) {
            refuel(amount() + accepted, null);
        }
        return Math.max(0, accepted);
    }

    /** Takes energy out of the fuel amount; returns how much was removed. */
    public int discharge(int energy, boolean simulate) {
        if (!this.amount.isWritable() || energy <= 0) {
            return 0;
        }
        int removed = Math.min(energy, amount());
        if (removed > 0 && !simulate) {
            setAmount(amount() - removed);
        }
        return Math.max(0, removed);
    }

    public int capacity() {
        VehicleSpec info = this.vehicle.spec();
        return info != null ? info.file().fuel().maxFuel() : 0;
    }

    public float percentage() {
        int capacity = capacity();
        return capacity == 0 ? 0.0F : (float) amount() / capacity;
    }

    public String fuelType() {
        return this.fuelType.get();
    }

    /** Sets the fuel by config name; an unknown fluid falls back to the default fuel. */
    public void setFuelType(@Nullable String name) {
        if (name == null) {
            name = "";
        }
        if (!name.isBlank() && FuelFluids.byName(name) == null) {
            ModLogger.warn("Fluid {} does not exist, using the default fuel instead", name);
            name = FuelFluids.DEFAULT_FUEL_TYPE;
        }
        this.fuelType.set(name);
    }

    public void setFuelType(@Nullable Fluid fluid) {
        if (fluid != null) {
            this.fuelType.set(FuelFluids.nameOf(fluid));
        }
    }

    /** True when this vehicle runs on electricity rather than fluid fuel. */
    public boolean isElectric() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && info.file().fuel().electric();
    }

    /** True when battery items in the fuel slot may act as extended storage for this (electric) vehicle. */
    public boolean allowBattery() {
        return isElectric() && this.vehicle.spec().file().fuel().batteries();
    }

    /** The fluid in the tank, or null when electric or nothing is set. */
    @Nullable
    public Fluid fluid() {
        String fluidName = fuelType();
        if (isElectric() || fluidName.isBlank()) {
            return null;
        }
        Fluid fluid = FuelFluids.byName(fluidName);
        if (fluid == null) {
            ModLogger.warn("Fuel fluid {} does not exist", fluidName);
        }
        return fluid;
    }

    /** The tank contents, or null when empty or electric. */
    @Nullable
    public FluidAmount contents() {
        if (isElectric() || amount() == 0 || fuelType().isEmpty()) {
            return null;
        }
        Fluid fluid = FuelFluids.byName(fuelType());
        if (fluid == null) {
            ModLogger.warn("Fuel fluid {} does not exist", fuelType());
            return null;
        }
        return new FluidAmount(fluid, amount());
    }

    /** Consumption multiplier of the fluid currently in the tank. */
    public float consumptionFactor() {
        VehicleSpec info = this.vehicle.spec();
        return info != null ? info.getFuelConsumption(fuelType()) : 1.0F;
    }

    /** Empties the tank and forgets the fuel type; only the server writes them. */
    public void dump() {
        this.amount.set(0);
        this.fuelType.set("");
    }

    /** The tank seen by pipes and pumps, or null for electric vehicles and those without a tank. */
    @Nullable
    public FuelTankAdapter fluidTank() {
        return !isElectric() && capacity() > 0 ? this.tank : null;
    }

    boolean canAcceptFuel(@Nullable Fluid fluid) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null || fluid == null || !info.isFuelValid(fluid)) {
            return false;
        }
        Fluid current = fluid();
        return amount() <= 0 || current == null || current == fluid;
    }

    /** True while there is fuel in the inventory slot that the tank could still take. */
    public boolean hasPendingWork() {
        return this.refill.hasPendingWork();
    }

    /** Server tick: burn fuel, then refill from the fuel slot. */
    @Override
    public void serverTick(TickContext ctx) {
        if (capacity() == 0 || this.vehicle.isDestroyed()) {
            return;
        }
        if (this.suppliedGraceTicks > 0) {
            this.suppliedGraceTicks--;
        }
        burn();
        this.refill.tick();
    }

    private void burn() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null || this.lifecycle.age() % CONSUMPTION_INTERVAL != 0 || amount() <= 1
                || this.throttle.get() <= 0.0 || this.suppliedGraceTicks > 0) {
            return;
        }
        double throttleFactor = Math.min(this.throttle.get() * 1.4, 1.0);
        this.pendingConsumption += throttleFactor * info.file().fuel().consumption() * consumptionFactor();
        if (this.pendingConsumption > 1.0) {
            int consumed = (int) this.pendingConsumption;
            this.pendingConsumption -= consumed;
            setAmount(amount() - consumed);
        }
    }

    /** Server: tankers ({@code fuelSupplyRange} > 0) hand fuel to every vehicle in range. */
    public void supplyNearby() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null || info.file().fuel().supplyRange() <= 0.0F
                || this.lifecycle.age() % TANKER_SUPPLY_INTERVAL != 0) {
            return;
        }
        Fluid fluid = info.file().fuel().infiniteSupply() ? FuelFluids.byName(info.file().fuel().supplyType())
                : fluid();
        if (fluid == null || !info.file().fuel().infiniteSupply() && amount() <= 0) {
            return;
        }

        float range = info.file().fuel().supplyRange();
        List<AircraftEntity> receivers = ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.body.hullBox().inflate(range, range, range));
        for (AircraftEntity receiver : receivers) {
            FuelComponent other = VehiclePeers.fuelTank(receiver);
            if (EntityIds.same(ac, receiver) || !other.canAcceptFuel(fluid)) {
                continue;
            }
            if ((!ac.onGround() || VehicleMotion.isParkedForSupply(receiver)) && other.amount() < other.capacity()) {
                int amount = Math.min(other.capacity() - other.amount(), TANKER_SUPPLY_AMOUNT);
                if (other.amount() <= 0) {
                    other.setFuelType(fluid);
                }
                other.refuel(other.amount() + amount, ac);
                if (!info.file().fuel().infiniteSupply()) {
                    setAmount(Math.max(0, amount() - amount));
                }
            }
            other.suppliedGraceTicks = SUPPLIED_GRACE_TICKS;
        }
    }

    @Override
    public void describe(StateSink state) {
        state.put("fuel", amount() + " / " + capacity());
        state.put("type", isElectric() ? "electric" : fuelType().isBlank() ? "-" : fuelType());
        state.put("infinite", this.status.get(StatusWord.INFINITE_FUEL));
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt(VehicleNbtCodec.FUEL, amount());
        tag.putString(VehicleNbtCodec.FUEL_TYPE, fuelType());
    }

    /** The type first: it decides which fluid the amount belongs to. Old saves default to our fuel. */
    @Override
    public void load(CompoundTag tag) {
        setFuelType(tag.contains(VehicleNbtCodec.FUEL_TYPE) ? tag.getString(VehicleNbtCodec.FUEL_TYPE)
                : FuelFluids.DEFAULT_FUEL_TYPE);
        setAmount(tag.getInt(VehicleNbtCodec.FUEL));
    }
}
