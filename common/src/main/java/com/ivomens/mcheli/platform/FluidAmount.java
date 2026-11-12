package com.ivomens.mcheli.platform;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** An amount of one fluid in millibuckets (1000 per bucket). */
public record FluidAmount(Fluid fluid, int amount) {

    public static final FluidAmount EMPTY = new FluidAmount(Fluids.EMPTY, 0);

    public boolean isEmpty() {
        return this.fluid == Fluids.EMPTY || this.amount <= 0;
    }
}
