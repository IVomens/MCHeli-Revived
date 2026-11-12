package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.block.ChargerBlockEntity;
import com.ivomens.mcheli.block.ModBlockEntities;
import com.ivomens.mcheli.platform.EnergyStore;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Team Reborn Energy side of {@link FabricEnergyHelper}, loaded only when that mod is installed.
 * One unit of its energy counts as one unit of MCHeli's (one FE), the usual exchange between the two.
 */
final class TeamRebornEnergy {

    /** One adapter per charger, so every lookup in a transaction shares its pending changes. */
    private static final Map<ChargerBlockEntity, EnergyStorage> CHARGERS = Collections.synchronizedMap(new WeakHashMap<>());

    private TeamRebornEnergy() {
    }

    static void registerCharger() {
        EnergyStorage.SIDED.registerForBlockEntity((charger, side) -> CHARGERS.computeIfAbsent(charger, ToTeamReborn::new),
                ModBlockEntities.CHARGER.get());
    }

    @Nullable
    static EnergyStore itemEnergy(ItemStack stack) {
        StackSlot slot = StackSlot.of(stack);
        EnergyStorage storage = slot.context().find(EnergyStorage.ITEM);
        return storage == null ? null : new FromTeamReborn(storage, slot, stack);
    }

    private static int clamp(long amount) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
    }

    /**
     * An item's energy storage as an MCHeli store. Each call is its own transaction; a committed change
     * is copied back onto the stack MCHeli handed in, as NeoForge's item capability writes to its stack.
     */
    private record FromTeamReborn(EnergyStorage storage, StackSlot slot, ItemStack original) implements EnergyStore {

        @Override
        public int receive(int maxReceive, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                return clamp(finish(this.storage.insert(maxReceive, transaction), transaction, simulate));
            }
        }

        @Override
        public int extract(int maxExtract, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                return clamp(finish(this.storage.extract(maxExtract, transaction), transaction, simulate));
            }
        }

        private long finish(long moved, Transaction transaction, boolean simulate) {
            if (!simulate && moved > 0) {
                transaction.commit();
                ItemStack written = this.slot.stack();
                if (written.is(this.original.getItem())) {
                    this.original.applyComponents(written.getComponentsPatch());
                    this.original.setCount(written.getCount());
                }
            }
            return moved;
        }

        @Override
        public int getStored() {
            return clamp(this.storage.getAmount());
        }

        @Override
        public int getCapacity() {
            return clamp(this.storage.getCapacity());
        }

        @Override
        public boolean canReceive() {
            return this.storage.supportsInsertion();
        }

        @Override
        public boolean canExtract() {
            return this.storage.supportsExtraction();
        }
    }

    /**
     * An MCHeli store (the charger) as a transactional energy storage. Changes are only worked out while
     * the transaction runs and are applied to the store when the outermost transaction commits.
     */
    private static final class ToTeamReborn extends SnapshotParticipant<ToTeamReborn.Pending> implements EnergyStorage {

        private final EnergyStore store;
        private Pending pending = Pending.NONE;

        ToTeamReborn(EnergyStore store) {
            this.store = store;
        }

        @Override
        public boolean supportsInsertion() {
            return this.store.canReceive();
        }

        @Override
        public boolean supportsExtraction() {
            return this.store.canExtract();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            int total = this.store.receive(clamp(this.pending.in() + maxAmount), true);
            int accepted = Math.max(0, total - this.pending.in());
            if (accepted > 0) {
                updateSnapshots(transaction);
                this.pending = new Pending(this.pending.in() + accepted, this.pending.out());
            }
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            int total = this.store.extract(clamp(this.pending.out() + maxAmount), true);
            int extracted = Math.max(0, total - this.pending.out());
            if (extracted > 0) {
                updateSnapshots(transaction);
                this.pending = new Pending(this.pending.in(), this.pending.out() + extracted);
            }
            return extracted;
        }

        @Override
        public long getAmount() {
            return (long) this.store.getStored() + this.pending.in() - this.pending.out();
        }

        @Override
        public long getCapacity() {
            return this.store.getCapacity();
        }

        @Override
        protected Pending createSnapshot() {
            return this.pending;
        }

        @Override
        protected void readSnapshot(Pending snapshot) {
            this.pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            Pending applied = this.pending;
            this.pending = Pending.NONE;
            if (applied.in() > 0) {
                this.store.receive(applied.in(), false);
            }
            if (applied.out() > 0) {
                this.store.extract(applied.out(), false);
            }
        }

        private record Pending(int in, int out) {
            static final Pending NONE = new Pending(0, 0);
        }
    }
}
