package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.EnergyHelper;
import com.ivomens.mcheli.platform.EnergyStore;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/** Item energy through the NeoForge energy capability; FE and MCHeli's unit are the same. */
public class NeoForgeEnergyHelper implements EnergyHelper {

    @Nullable
    @Override
    public EnergyStore getItemEnergy(ItemStack stack) {
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? null : new FromNeoForge(storage);
    }

    /** A MCHeli energy store (the charger) as a NeoForge energy storage. */
    record ToNeoForge(EnergyStore store) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return this.store.receive(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return this.store.extract(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return this.store.getStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.store.getCapacity();
        }

        @Override
        public boolean canExtract() {
            return this.store.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.store.canReceive();
        }
    }

    private record FromNeoForge(IEnergyStorage storage) implements EnergyStore {

        @Override
        public int receive(int maxReceive, boolean simulate) {
            return this.storage.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extract(int maxExtract, boolean simulate) {
            return this.storage.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getStored() {
            return this.storage.getEnergyStored();
        }

        @Override
        public int getCapacity() {
            return this.storage.getMaxEnergyStored();
        }

        @Override
        public boolean canReceive() {
            return this.storage.canReceive();
        }

        @Override
        public boolean canExtract() {
            return this.storage.canExtract();
        }
    }
}
