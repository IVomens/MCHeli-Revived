package com.ivomens.mcheli.platform;

/**
 * A single fluid tank, in millibuckets. Loader glue exposes it to pipes and pumps (the fluid handler
 * capability on NeoForge).
 */
public interface FluidTank {

    /** The contents, or {@link FluidAmount#EMPTY}. */
    FluidAmount getContents();

    int getCapacity();

    /** Whether the tank would take this fluid at all, however full it is. */
    boolean isFluidValid(FluidAmount resource);

    /** @return the amount taken, or that would be taken when simulating */
    int fill(FluidAmount resource, boolean simulate);

    /** Drains up to {@code resource.amount()} if the tank holds that fluid. */
    FluidAmount drain(FluidAmount resource, boolean simulate);

    /** Drains up to {@code maxAmount} of whatever the tank holds. */
    FluidAmount drain(int maxAmount, boolean simulate);
}
