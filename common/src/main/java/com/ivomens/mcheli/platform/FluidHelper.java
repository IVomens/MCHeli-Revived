package com.ivomens.mcheli.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/** Fluid containers from other mods (buckets, cans, tank items) and fluid names. */
public interface FluidHelper {

    /** True when the stack is a fluid container, empty or not. */
    boolean isFluidContainer(ItemStack stack);

    /** The non-empty contents of a fluid container item, one entry per tank. */
    List<FluidAmount> getContents(ItemStack stack);

    /**
     * Drains one container item: the first fluid in it that {@code accepts} allows, up to
     * {@code maxAmount}. The passed stack may be changed, so pass a copy holding a single item.
     *
     * @return the drained fluid and the container left over, or null when nothing was drained
     */
    @Nullable
    DrainedContainer drain(ItemStack container, Predicate<Fluid> accepts, int maxAmount);

    /** The fluid's display name, e.g. "Water". */
    Component getDisplayName(Fluid fluid);

    record DrainedContainer(FluidAmount drained, ItemStack container) {
    }
}
