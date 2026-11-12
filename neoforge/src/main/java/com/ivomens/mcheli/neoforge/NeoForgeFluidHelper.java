package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.platform.FluidHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Fluid container items through the NeoForge fluid handler item capability. */
public class NeoForgeFluidHelper implements FluidHelper {

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    @Override
    public List<FluidAmount> getContents(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return List.of();
        }
        List<FluidAmount> contents = new ArrayList<>(handler.getTanks());
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (!fluid.isEmpty()) {
                contents.add(new FluidAmount(fluid.getFluid(), fluid.getAmount()));
            }
        }
        return contents;
    }

    @Nullable
    @Override
    public DrainedContainer drain(ItemStack container, Predicate<Fluid> accepts, int maxAmount) {
        IFluidHandlerItem handler = container.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return null;
        }
        Fluid fluid = findDrainable(handler, accepts);
        if (fluid == null) {
            return null;
        }
        FluidStack drained = handler.drain(new FluidStack(fluid, maxAmount), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return null;
        }
        return new DrainedContainer(new FluidAmount(fluid, drained.getAmount()), handler.getContainer());
    }

    @Nullable
    private static Fluid findDrainable(IFluidHandlerItem handler, Predicate<Fluid> accepts) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (!contents.isEmpty() && accepts.test(contents.getFluid())
                    && !handler.drain(contents.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                return contents.getFluid();
            }
        }
        return null;
    }

    @Override
    public Component getDisplayName(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }
}
