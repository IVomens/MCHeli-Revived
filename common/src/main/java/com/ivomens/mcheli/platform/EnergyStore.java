package com.ivomens.mcheli.platform;

/**
 * An energy buffer in MCHeli's internal unit, which equals one FE. Loader glue exposes blocks that
 * implement it to cables and generators (the energy capability on NeoForge).
 */
public interface EnergyStore {

    /** @return the energy accepted, or that would be accepted when simulating */
    int receive(int maxReceive, boolean simulate);

    /** @return the energy removed, or that would be removed when simulating */
    int extract(int maxExtract, boolean simulate);

    int getStored();

    int getCapacity();

    boolean canReceive();

    boolean canExtract();
}
