package com.ivomens.mcheli.gametest.neoforge;

import com.ivomens.mcheli.gametest.ExposedStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/** The entity item and fluid handler capabilities. */
public class NeoForgeExposedStorage implements ExposedStorage {

    @Nullable
    @Override
    public Slots items(Entity entity) {
        IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
        return handler == null ? null : new Slots() {
            @Override
            public int size() {
                return handler.getSlots();
            }

            @Override
            public ItemStack get(int slot) {
                return handler.getStackInSlot(slot);
            }

            @Override
            public ItemStack insert(int slot, ItemStack stack) {
                return handler.insertItem(slot, stack, false);
            }
        };
    }

    @Nullable
    @Override
    public Tank fluids(Entity entity) {
        IFluidHandler handler = entity.getCapability(Capabilities.FluidHandler.ENTITY, null);
        return handler == null ? null : new Tank() {
            @Override
            public int amount() {
                return handler.getFluidInTank(0).getAmount();
            }

            @Override
            public int fill(Fluid fluid, int millibuckets, boolean simulate) {
                return handler.fill(new FluidStack(fluid, millibuckets),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }
        };
    }
}
