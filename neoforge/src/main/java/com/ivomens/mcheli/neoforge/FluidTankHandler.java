package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.platform.FluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A {@link FluidTank} as a NeoForge fluid handler with one tank. Data components are not kept. */
record FluidTankHandler(FluidTank tank) implements IFluidHandler {

    static FluidAmount toAmount(FluidStack stack) {
        return stack.isEmpty() ? FluidAmount.EMPTY : new FluidAmount(stack.getFluid(), stack.getAmount());
    }

    static FluidStack toStack(FluidAmount amount) {
        return amount.isEmpty() ? FluidStack.EMPTY : new FluidStack(amount.fluid(), amount.amount());
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return toStack(this.tank.getContents());
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.tank.getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.tank.isFluidValid(toAmount(stack));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.tank.fill(toAmount(resource), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return toStack(this.tank.drain(toAmount(resource), action.simulate()));
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return toStack(this.tank.drain(maxDrain, action.simulate()));
    }
}
