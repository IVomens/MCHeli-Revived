package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.platform.FluidHelper;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fluid container items through the Transfer API. MCHeli counts fluid in millibuckets, the Transfer
 * API in droplets (81 000 per bucket), so amounts are converted at this edge and only whole
 * millibuckets are ever drained. As on NeoForge, a container is drainable only if it gives up a single
 * millibucket, which leaves out plain buckets (they only empty all at once).
 */
public class FabricFluidHelper implements FluidHelper {

    /** Droplets per millibucket. */
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return storage(StackSlot.of(stack)) != null;
    }

    @Override
    public List<FluidAmount> getContents(ItemStack stack) {
        Storage<FluidVariant> storage = storage(StackSlot.of(stack));
        if (storage == null) {
            return List.of();
        }
        List<FluidAmount> contents = new ArrayList<>();
        for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
            int millibuckets = toMillibuckets(view.getAmount());
            if (millibuckets > 0) {
                contents.add(new FluidAmount(view.getResource().getFluid(), millibuckets));
            }
        }
        return contents;
    }

    @Nullable
    @Override
    public DrainedContainer drain(ItemStack container, Predicate<Fluid> accepts, int maxAmount) {
        StackSlot slot = StackSlot.of(container);
        Storage<FluidVariant> storage = storage(slot);
        if (storage == null) {
            return null;
        }
        FluidVariant fluid = findDrainable(storage, accepts);
        if (fluid == null) {
            return null;
        }
        long wanted = StorageUtil.simulateExtract(storage, fluid, (long) maxAmount * DROPLETS_PER_MB, null);
        wanted -= wanted % DROPLETS_PER_MB;
        if (wanted <= 0) {
            return null;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            if (storage.extract(fluid, wanted, transaction) != wanted) {
                return null;
            }
            transaction.commit();
        }
        return new DrainedContainer(new FluidAmount(fluid.getFluid(), toMillibuckets(wanted)), slot.stack());
    }

    @Nullable
    private static FluidVariant findDrainable(Storage<FluidVariant> storage, Predicate<Fluid> accepts) {
        for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
            FluidVariant fluid = view.getResource();
            if (accepts.test(fluid.getFluid())
                    && StorageUtil.simulateExtract(storage, fluid, DROPLETS_PER_MB, null) == DROPLETS_PER_MB) {
                return fluid;
            }
        }
        return null;
    }

    @Override
    public Component getDisplayName(Fluid fluid) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid));
    }

    @Nullable
    private static Storage<FluidVariant> storage(StackSlot slot) {
        return slot.stack().isEmpty() ? null : slot.context().find(FluidStorage.ITEM);
    }

    private static int toMillibuckets(long droplets) {
        return (int) Math.min(Integer.MAX_VALUE, droplets / DROPLETS_PER_MB);
    }
}
