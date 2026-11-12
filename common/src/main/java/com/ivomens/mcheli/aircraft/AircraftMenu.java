package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.compat.energy.EnergyCompat;
import com.ivomens.mcheli.entity.ItemSlots;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.aircraft.parachute.ParachuteItem;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Classic aircraft menu: three fuel slots, parachute slots for ejection seats, player inventory. */
public class AircraftMenu extends AbstractContainerMenu {

    private static final int FUEL_SLOTS = 3;
    private static final int PLAYER_INV_START_Y = 135;
    private static final int HOTBAR_Y = 195;
    private static final int PARACHUTE_SLOT_START = 3;

    public final Player player;
    public final AircraftEntity aircraft;
    private final ItemSlots inventory;

    public AircraftMenu(int id, Inventory playerInventory, AircraftEntity aircraft) {
        super(AircraftMenuTypes.AIRCRAFT_LEGACY.get(), id);
        this.player = playerInventory.player;
        this.aircraft = aircraft;
        this.inventory = aircraft.getGuiInventory().getItemHandler();

        addAircraftSlots();
        addPlayerInventory(playerInventory);
    }

    /** Client constructor: the aircraft entity id is sent by the server. */
    public AircraftMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, resolve(playerInventory.player, buf.readInt()));
    }

    private static AircraftEntity resolve(Player player, int entityId) {
        if (player.level().getEntity(entityId) instanceof AircraftEntity ac) return ac;
        throw new IllegalStateException("Aircraft " + entityId + " is not loaded on the client");
    }

    private void addAircraftSlots() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            addSlot(new ItemSlots.MenuSlot(inventory, i, 10, 30 + i * 18));
        }
        int seats = aircraft.ejectionSeats().count();
        for (int i = 0; i < seats; i++) {
            addSlot(new ItemSlots.MenuSlot(inventory, PARACHUTE_SLOT_START + i, 10 + i * 18, 105));
        }
    }

    private void addPlayerInventory(Inventory inv) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(inv, 9 + x + y * 9, 25 + x * 18, PLAYER_INV_START_Y + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(inv, x, 25 + x * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (aircraft.stillValid(player)) {
            return true;
        }
        if (aircraft.isUAV() && aircraft.uavLink().station() instanceof IUavStation station) {
            double x = station.launchPoint().x;
            double z = station.launchPoint().z;
            return aircraft.getX() > x - 10 && aircraft.getX() < x + 10
                    && aircraft.getZ() > z - 10 && aircraft.getZ() < z + 10;
        }
        return false;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int aircraftSlots = PARACHUTE_SLOT_START + aircraft.ejectionSeats().count();

        if (index < aircraftSlots) {
            if (!moveItemStackTo(stack, aircraftSlots, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (isFuelSlotItem(stack)) {
            if (!moveItemStackTo(stack, 0, FUEL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ParachuteItem) {
            if (!moveItemStackTo(stack, PARACHUTE_SLOT_START, aircraftSlots, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;

        // Reject anything in the fuel slots that isn't a valid fuel/energy item.
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && !isFuelSlotItem(stack)) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
                player.drop(stack, false);
            }
        }
        int seats = aircraft.ejectionSeats().count();
        for (int i = 0; i < seats; i++) {
            ejectIfInvalid(player, PARACHUTE_SLOT_START + i, ParachuteItem.class);
        }
    }

    /**
     * Items accepted by the fuel/charge slots: the MCHeli fuel item, any fluid container
     * (for fluid-fuelled aircraft), or (for electric aircraft that allow it) energy battery items.
     */
    private boolean isFuelSlotItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof FuelItem) return true;
        if (Services.FLUID.isFluidContainer(stack)) return true;
        return aircraft.fuel().allowBattery() && EnergyCompat.isBattery(stack);
    }

    private void ejectIfInvalid(Player player, int slot, Class<?> allowed) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!stack.isEmpty() && !allowed.isInstance(stack.getItem())) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            player.drop(stack, false);
        }
    }
}
