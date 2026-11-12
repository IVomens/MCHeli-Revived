package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.compat.energy.EnergyCompat;
import com.ivomens.mcheli.entity.ItemSlots;
import com.ivomens.mcheli.platform.FluidAmount;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.aircraft.parachute.ParachuteItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The aircraft panel's menu: fuel in and out, parachutes, the scrolling trunk, the weapons and the player
 * inventory, or only the trunk. Opened through {@link com.ivomens.mcheli.aircraft.AircraftGuiFactory};
 * the client draws it with the panel or trunk screen.
 */
public class AircraftPanelMenu extends AbstractContainerMenu {

    public static final int SLOT_SIZE = 18;
    /** Menu button ids (sent with {@code clickMenuButton}). */
    public static final int BTN_DUMP_FUEL = 0;
    public static final int BTN_SCROLL_UP = 1;
    public static final int BTN_SCROLL_DOWN = 2;
    private static final Predicate<ItemStack> IS_PARACHUTE = stack -> stack.getItem() instanceof ParachuteItem;


    public final AircraftEntity aircraft;
    public final boolean containerOnly;
    public final ItemSlots trunk;
    public final int trunkColumns;
    public final int trunkVisibleRows;
    public final int trunkRows;
    public final int trunkX;
    public final int trunkY;
    public final int imageWidth;
    public final int imageHeight;
    public final int inventoryY;
    public final boolean showParachuteSlot;
    public final List<WeaponEntry> weaponEntries;
    int scrollRow;
    private int aircraftSlotEnd;
    private int fuelInSlot = -1;
    private int parachuteSlotStart = -1;
    private int trunkSlotStart;
    private int trunkSlotEnd;

    public AircraftPanelMenu(int id, Inventory inv, AircraftEntity aircraft, boolean containerOnly) {
        super(AircraftMenuTypes.AIRCRAFT.get(), id);
        this.aircraft = aircraft;
        this.containerOnly = containerOnly;
        this.trunk = aircraft.getInventory();
        VehicleSpec info = aircraft.spec();
        this.showParachuteSlot = !containerOnly && info != null
                && (info.dropsCrew() || info.file().features().ejectionSeat());

        List<WeaponEntry> entries = containerOnly ? List.of() : reloadableWeapons(aircraft);
        this.weaponEntries = entries;

        int total = this.trunk.getSlots();
        if (containerOnly) {
            this.trunkColumns = Math.max(1, Math.min(total, 9));
            this.trunkX = 8;
            this.trunkY = 18;
        } else {
            this.trunkColumns = 7;
            this.trunkX = 214;
            this.trunkY = 18;
        }
        this.trunkRows = total == 0 ? 0 : (total + this.trunkColumns - 1) / this.trunkColumns;
        this.trunkVisibleRows = Math.min(this.trunkRows, 5);

        if (containerOnly) {
            this.imageWidth = Math.max(176, this.trunkColumns * SLOT_SIZE + 30);
            this.inventoryY = this.trunkY + Math.max(1, this.trunkVisibleRows) * SLOT_SIZE + 14;
        } else {
            this.imageWidth = total > 0 ? 348 : 214;
            int listRows = Math.min(4, entries.size());
            this.inventoryY = 118 + listRows * 22 + 12;
        }
        this.imageHeight = this.inventoryY + 3 * SLOT_SIZE + 4 + SLOT_SIZE + 8;

        if (!containerOnly) {
            this.addPanelSlots(aircraft, info);
        }
        this.addTrunkSlots();
        this.addPlayerSlots(inv);
    }

    /** The weapons with a magazine, which the panel lists with their resupply buttons. */
    private static List<WeaponEntry> reloadableWeapons(AircraftEntity aircraft) {
        List<WeaponEntry> entries = new ArrayList<>();
        WeaponGroup[] weapons = aircraft.weapons().all();
        for (int i = 0; i < weapons.length; i++) {
            if (weapons[i] != null && weapons[i].magazineSize() > 0) entries.add(new WeaponEntry(i, weapons[i]));
        }
        return entries;
    }

    /** Fuel in and out, and the parachutes when the aircraft has them. */
    private void addPanelSlots(AircraftEntity aircraft, VehicleSpec info) {
        ItemSlots gui = aircraft.getGuiInventory().getItemHandler();
        this.fuelInSlot = this.slots.size();
        Predicate<ItemStack> fuel = fuelPredicate(info);
        this.addSlot(new ItemSlots.MenuSlot(gui, AircraftInventory.SLOT_FUEL_IN, 10, 86) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return fuel.test(stack);
            }
        });
        this.addSlot(new ItemSlots.MenuSlot(gui, AircraftInventory.SLOT_FUEL_OUT, 66, 86) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });
        if (this.showParachuteSlot) {
            this.parachuteSlotStart = this.slots.size();
            for (int i = 0; i < 2; i++) {
                this.addSlot(new ItemSlots.MenuSlot(gui, AircraftInventory.SLOT_PARACHUTE0 + i, 98 + i * SLOT_SIZE, 94) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return IS_PARACHUTE.test(stack);
                    }
                });
            }
        }
    }

    /** The visible window onto the trunk; its slots follow the scroll position. */
    private void addTrunkSlots() {
        this.trunkSlotStart = this.slots.size();
        for (int r = 0; r < this.trunkVisibleRows; r++) {
            for (int c = 0; c < this.trunkColumns; c++) {
                this.addSlot(new ScrollSlot(this, this.trunk, r * this.trunkColumns + c, this.trunkColumns,
                        this.trunkX + 1 + c * SLOT_SIZE, this.trunkY + 1 + r * SLOT_SIZE));
            }
        }
        this.trunkSlotEnd = this.slots.size();
        this.aircraftSlotEnd = this.slots.size();
    }

    private void addPlayerSlots(Inventory inv) {
        int invX = (this.imageWidth - 9 * SLOT_SIZE) / 2;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new Slot(inv, 9 + x + y * 9, invX + x * SLOT_SIZE, this.inventoryY + y * SLOT_SIZE));
            }
        }
        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(inv, x, invX + x * SLOT_SIZE, this.inventoryY + 3 * SLOT_SIZE + 4));
        }
    }

    public AircraftPanelMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolve(inv.player, buf.readInt()), buf.readBoolean());
    }

    private static AircraftEntity resolve(Player player, int entityId) {
        if (player.level().getEntity(entityId) instanceof AircraftEntity ac) return ac;
        throw new IllegalStateException("Aircraft " + entityId + " is not loaded on the client");
    }

    public int getScrollRow() {
        return this.scrollRow;
    }

    public int getMaxScrollRow() {
        return Math.max(0, this.trunkRows - this.trunkVisibleRows);
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        switch (id) {
            case BTN_DUMP_FUEL -> {
                if (!this.containerOnly) this.aircraft.fuel().dump();
                return true;
            }
            case BTN_SCROLL_UP -> {
                this.scrollRow = Math.max(0, this.scrollRow - 1);
                return true;
            }
            case BTN_SCROLL_DOWN -> {
                this.scrollRow = Math.min(this.getMaxScrollRow(), this.scrollRow + 1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.aircraft.isAlive() && player.level() == this.aircraft.level()
                && player.distanceToSqr(this.aircraft) <= 64 * 64;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < this.aircraftSlotEnd) {
            if (slot instanceof ScrollSlot scrollSlot) {
                // Simulate the extraction first so only what fits in the inventory leaves the trunk.
                ItemStack extracted = this.trunk.extractItem(scrollSlot.index(), stack.getCount(), true);
                ItemStack moving = extracted.copy();
                if (!this.moveItemStackTo(moving, this.aircraftSlotEnd, this.slots.size(), true)) return ItemStack.EMPTY;
                this.trunk.extractItem(scrollSlot.index(), extracted.getCount() - moving.getCount(), false);
                return original;
            }
            if (!this.moveItemStackTo(stack, this.aircraftSlotEnd, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (this.fuelInSlot >= 0 && this.slots.get(this.fuelInSlot).mayPlace(stack)) {
                moved = this.moveItemStackTo(stack, this.fuelInSlot, this.fuelInSlot + 1, false);
            }
            if (!moved && this.parachuteSlotStart >= 0 && IS_PARACHUTE.test(stack)) {
                moved = this.moveItemStackTo(stack, this.parachuteSlotStart, this.parachuteSlotStart + 2, false);
            }
            if (!moved && this.trunk.getSlots() > 0) {
                ItemStack rest = stack.copy();
                for (int i = 0; i < this.trunk.getSlots() && !rest.isEmpty(); i++) {
                    rest = this.trunk.insertItem(i, rest, false);
                }
                moved = rest.getCount() != stack.getCount();
                stack.setCount(rest.getCount());
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    public static Predicate<ItemStack> fuelPredicate(VehicleSpec info) {
        return stack -> {
            if (stack.isEmpty() || info == null) return false;
            if (stack.getItem() instanceof FuelItem) return true;
            // Electric aircraft accept energy battery items in the fuel/charge slot.
            if (info.file().fuel().electric() && info.file().fuel().batteries()
                    && EnergyCompat.isBattery(stack)) return true;

            for (FluidAmount contents : Services.FLUID.getContents(stack)) {
                if (!contents.isEmpty() && info.isFuelValid(contents.fluid())) return true;
            }
            return false;
        };
    }

    public static boolean canResupply(WeaponGroup ws, AircraftEntity aircraft, Player player) {
        return aircraft.ammoSupply().canPlayerSupply(player, ws) && ws.ammo() < ws.magazineSize();
    }

    @Nullable
    public static Fluid fluidByName(String name) {
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl == null) return null;
        return BuiltInRegistries.FLUID.getOptional(rl).orElse(null);
    }

    public static String fluidName(@Nullable Fluid fluid) {
        return fluid == null ? "Empty" : Services.FLUID.getDisplayName(fluid).getString();
    }

    /** Slot whose backing index follows the trunk scroll offset. */
    static class ScrollSlot extends Slot {
        private final ItemSlots handler;
        private final int baseIndex;
        private final int columns;
        private final AircraftPanelMenu menu;

        ScrollSlot(AircraftPanelMenu menu, ItemSlots handler, int baseIndex, int columns, int x, int y) {
            super(new SimpleContainer(0), baseIndex, x, y);
            this.menu = menu;
            this.handler = handler;
            this.baseIndex = baseIndex;
            this.columns = columns;
        }

        int index() {
            return this.baseIndex + this.menu.scrollRow * this.columns;
        }

        @Override
        public boolean isActive() {
            return index() < this.handler.getSlots();
        }

        @Override
        public @NotNull ItemStack getItem() {
            int i = index();
            return i < this.handler.getSlots() ? this.handler.getStackInSlot(i) : ItemStack.EMPTY;
        }

        @Override
        public boolean hasItem() {
            return !this.getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            int i = index();
            return i < this.handler.getSlots() && this.handler.isItemValid(i, stack);
        }

        @Override
        public void set(@NotNull ItemStack stack) {
            int i = index();
            if (i < this.handler.getSlots()) {
                this.handler.setStackInSlot(i, stack);
                this.setChanged();
            }
        }

        @Override
        public void setByPlayer(@NotNull ItemStack newStack, @NotNull ItemStack oldStack) {
            this.set(newStack);
        }

        @Override
        public int getMaxStackSize() {
            int i = index();
            return i < this.handler.getSlots() ? this.handler.getSlotLimit(i) : 0;
        }

        @Override
        public int getMaxStackSize(@NotNull ItemStack stack) {
            return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            int i = index();
            return i < this.handler.getSlots() && !this.handler.extractItem(i, 1, true).isEmpty();
        }

        @Override
        public @NotNull ItemStack remove(int amount) {
            int i = index();
            return i < this.handler.getSlots() ? this.handler.extractItem(i, amount, false) : ItemStack.EMPTY;
        }
    }

    public record WeaponEntry(int id, WeaponGroup set) {
    }
}
