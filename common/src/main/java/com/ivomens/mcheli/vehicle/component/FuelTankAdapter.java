package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.platform.FluidTank;
import net.minecraft.world.level.material.Fluid;

/**
 * The fuel tank as pipes and pumps see it. Clients never change the tank; they answer with nothing
 * filled or drained.
 */
public final class FuelTankAdapter implements FluidTank {

    private final AircraftEntity vehicle;
    private final FuelComponent fuel;

    FuelTankAdapter(AircraftEntity vehicle, FuelComponent fuel) {
        this.vehicle = vehicle;
        this.fuel = fuel;
    }

    @Override
    public FluidAmount getContents() {
        FluidAmount contents = this.fuel.contents();
        return contents != null ? contents : FluidAmount.EMPTY;
    }

    @Override
    public int getCapacity() {
        return this.fuel.capacity();
    }

    @Override
    public boolean isFluidValid(FluidAmount resource) {
        VehicleSpec info = this.vehicle.spec();
        return info != null && !this.fuel.isElectric() && !resource.isEmpty() && info.isFuelValid(resource.fluid());
    }

    @Override
    public int fill(FluidAmount resource, boolean simulate) {
        VehicleSpec info = this.vehicle.spec();
        // isFuelValid also rejects water, lava and anything else this vehicle cannot burn
        if (info == null || resource.isEmpty() || this.vehicle.level().isClientSide || this.fuel.isElectric()
                || !info.isFuelValid(resource.fluid())) {
            return 0;
        }

        int capacity = this.fuel.capacity();
        int contents = this.fuel.amount();
        boolean sameFuel = FuelFluids.nameOf(resource.fluid()).equalsIgnoreCase(this.fuel.fuelType());
        if (contents >= capacity || contents > 0 && !sameFuel) {
            return 0;
        }

        int accepted = Math.min(resource.amount(), capacity - contents);
        if (!simulate && accepted > 0) {
            if (contents <= 0) {
                this.fuel.setFuelType(resource.fluid());
            }
            this.fuel.refuel(contents + accepted, null);
        }
        return accepted;
    }

    @Override
    public FluidAmount drain(FluidAmount resource, boolean simulate) {
        if (resource.isEmpty() || !FuelFluids.nameOf(resource.fluid()).equalsIgnoreCase(this.fuel.fuelType())) {
            return FluidAmount.EMPTY;
        }
        return drain(resource.fluid(), resource.amount(), simulate);
    }

    @Override
    public FluidAmount drain(int maxAmount, boolean simulate) {
        Fluid fluid = FuelFluids.byName(this.fuel.fuelType());
        return maxAmount > 0 && fluid != null ? drain(fluid, maxAmount, simulate) : FluidAmount.EMPTY;
    }

    private FluidAmount drain(Fluid fluid, int amount, boolean simulate) {
        int contents = this.fuel.amount();
        if (this.fuel.isElectric() || this.vehicle.level().isClientSide || contents <= 0) {
            return FluidAmount.EMPTY;
        }
        int drained = Math.min(amount, contents);
        if (!simulate) {
            this.fuel.setAmount(contents - drained);
        }
        return new FluidAmount(fluid, drained);
    }
}
