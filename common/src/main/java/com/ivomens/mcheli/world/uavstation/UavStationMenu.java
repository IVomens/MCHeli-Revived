package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.world.WorldMenuTypes;
import com.ivomens.mcheli.world.uavstation.UavStationGuiData.UavEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The UAV station's menu: the paired UAVs as the server saw them on opening, the legacy "deploy a UAV
 * item" slot and the player inventory. The constants place the slots, and the screen draws around them.
 */
public class UavStationMenu extends AbstractContainerMenu {

    public static final int LIST_X = 8;
    public static final int LIST_Y = 18;
    public static final int LIST_WIDTH = 230;
    public static final int ROW_HEIGHT = 22;
    public static final int LIST_ROWS = 6;
    public static final int WIDTH = 362;
    public static final int LEGACY_SLOT_Y = LIST_Y + LIST_ROWS * ROW_HEIGHT + 6;
    public static final int INVENTORY_Y = LEGACY_SLOT_Y + 28;
    public static final int HEIGHT = INVENTORY_Y + 3 * 18 + 4 + 18 + 8;
    private static final double MAX_DISTANCE_SQ = 64;

    public final UavStationEntity station;
    public final List<UavEntry> entries;

    public UavStationMenu(int id, Inventory inv, UavStationEntity station, List<UavEntry> entries) {
        super(WorldMenuTypes.UAV_STATION.get(), id);
        this.station = station;
        this.entries = entries;
        this.addSlot(new Slot(station, 0, LIST_X + 1, LEGACY_SLOT_Y + 1));
        int invX = (WIDTH - 9 * 18) / 2;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new Slot(inv, 9 + x + y * 9, invX + x * 18, INVENTORY_Y + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(inv, x, invX + x * 18, INVENTORY_Y + 3 * 18 + 4));
        }
    }

    public UavStationMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolve(inv.player, buf.readInt()), UavStationGuiData.readEntries(buf));
    }

    private static UavStationEntity resolve(Player player, int id) {
        if (player.level().getEntity(id) instanceof UavStationEntity station) return station;
        throw new IllegalStateException("UAV station " + id + " is not loaded on the client");
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !this.station.isRemoved() && player.level() == this.station.level()
                && player.distanceToSqr(this.station) <= MAX_DISTANCE_SQ;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
