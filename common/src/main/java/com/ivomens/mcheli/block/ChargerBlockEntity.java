package com.ivomens.mcheli.block;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.compat.energy.EnergyCompat;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.EnergyStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A powered block that charges nearby electric MCHeli aircraft (and battery items in their fuel slot).
 * <p>
 * It buffers energy in MCHeli's internal unit (FE-equivalent); the loader glue exposes it as a block
 * energy storage so cables and generators can push power in.
 */
public class ChargerBlockEntity extends BlockEntity implements EnergyStore {

    /** Nearby aircraft are charged every this many ticks. */
    private static final int CHARGE_INTERVAL = 10;

    /** Internal energy buffer (FE-equivalent units). */
    private int energy;
    private int ticksSinceCharge;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGER.get(), pos, state);
    }

    int getEnergyInternal() {
        return this.energy;
    }

    int getCapacityInternal() {
        return Math.max(0, McheliConfig.energy().chargerCapacity());
    }

    /** Adds internal energy to the buffer, capped at capacity. Returns the amount accepted. */
    private int insertInternal(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, this.getCapacityInternal() - this.energy));
        if (accepted > 0 && !simulate) {
            this.energy += accepted;
            this.setChanged();
        }
        return accepted;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargerBlockEntity charger) {
        if (++charger.ticksSinceCharge < CHARGE_INTERVAL) {
            return;
        }
        charger.ticksSinceCharge = 0;
        if (charger.energy > 0) {
            charger.chargeNearbyAircraft();
        }
    }

    private void chargeNearbyAircraft() {
        double range = McheliConfig.energy().chargerRange();
        if (this.level == null || range <= 0.0) {
            return;
        }
        List<AircraftEntity> aircraftInRange = this.level.getEntitiesOfClass(AircraftEntity.class,
                new AABB(this.worldPosition).inflate(range));

        int budget = Math.max(0, McheliConfig.energy().chargerTransferRate());
        int startEnergy = this.energy;
        for (AircraftEntity aircraft : aircraftInRange) {
            if (this.energy <= 0 || budget <= 0) {
                break;
            }
            if (!aircraft.fuel().isElectric()) {
                continue;
            }

            // Top up the aircraft's own buffer first.
            int wanted = aircraft.fuel().capacity() - aircraft.fuel().amount();
            if (wanted > 0) {
                int accepted = aircraft.fuel().charge(Math.min(Math.min(budget, this.energy), wanted), false);
                this.energy -= accepted;
                budget -= accepted;
            }

            // Whatever is left goes into a battery in the fuel slot.
            if (this.energy > 0 && budget > 0 && aircraft.fuel().allowBattery()) {
                ItemStack battery = aircraft.getGuiInventory().getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_IN);
                int stored = EnergyCompat.chargeToItem(battery, Math.min(budget, this.energy), false);
                this.energy -= stored;
                budget -= stored;
            }
        }
        if (this.energy != startEnergy) {
            this.setChanged();
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy = tag.getInt("Energy");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", this.energy);
    }

    @Override
    public int receive(int maxReceive, boolean simulate) {
        return this.canReceive() ? this.insertInternal(maxReceive, simulate) : 0;
    }

    @Override
    public int extract(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getStored() {
        return this.energy;
    }

    @Override
    public int getCapacity() {
        return this.getCapacityInternal();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
