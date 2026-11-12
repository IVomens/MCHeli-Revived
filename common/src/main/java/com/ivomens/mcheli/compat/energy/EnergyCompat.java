package com.ivomens.mcheli.compat.energy;

import com.ivomens.mcheli.platform.EnergyStore;
import com.ivomens.mcheli.platform.Services;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Battery items for electric aircraft. MCHeli's internal energy unit (stored in the aircraft's fuel
 * field) is FE-equivalent, so the loader's item energy is used as is.
 */
public final class EnergyCompat {

    private EnergyCompat() {}

    /** True when the stack stores energy. */
    public static boolean isBattery(ItemStack stack) {
        return stack != null && !stack.isEmpty() && energy(stack) != null;
    }

    /**
     * Pulls up to {@code maxInternal} units out of a battery item (to top up an aircraft).
     *
     * @return units actually obtained
     */
    public static int dischargeFromItem(ItemStack stack, int maxInternal, boolean simulate) {
        EnergyStore storage = maxInternal > 0 ? energy(stack) : null;
        return storage != null && storage.canExtract() ? Math.max(0, storage.extract(maxInternal, simulate)) : 0;
    }

    /**
     * Pushes up to {@code maxInternal} units into a battery item (overflow storage).
     *
     * @return units actually stored
     */
    public static int chargeToItem(ItemStack stack, int maxInternal, boolean simulate) {
        EnergyStore storage = maxInternal > 0 ? energy(stack) : null;
        return storage != null && storage.canReceive() ? Math.max(0, storage.receive(maxInternal, simulate)) : 0;
    }

    @Nullable
    private static EnergyStore energy(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : Services.ENERGY.getItemEnergy(stack);
    }
}
