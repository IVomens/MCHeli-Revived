package com.ivomens.mcheli.setup;

import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One of MCHeli's creative tabs: its icon cycles through its items (or shows a fixed, configurable
 * item) and its contents are the items registered into it, aircraft sorted by category and name.
 */
public class CreativeTab {

    /** Icon requests (one per rendered frame) before the cycling icon moves on to the next item. */
    private static final int ICON_SWITCH_DELAY = 60;

    private static final Comparator<ItemStack> DISPLAY_ORDER = (stack1, stack2) -> {
        if (stack1.getItem() instanceof AircraftItem aircraft1 && stack2.getItem() instanceof AircraftItem aircraft2) {
            VehicleSpec info1 = aircraft1.spec();
            VehicleSpec info2 = aircraft2.spec();
            if (info1 != null && info2 != null) {
                return (info1.file().category() + "." + info1.name()).compareTo(info2.file().category() + "."
                        + info2.name());
            }
        }
        return stack1.getItem().getDescriptionId().compareTo(stack2.getItem().getDescriptionId());
    };

    private final List<Item> items = new ArrayList<>();
    private final List<ItemStack> iconStacks = new ArrayList<>();
    private ItemStack currentIcon = ItemStack.EMPTY;
    private int currentIconIndex = 0;
    private int iconSwitchCountdown = 0;
    private ItemStack fixedIcon = null;

    private final CreativeModeTab tab;

    private CreativeTab(String label) {
        this.tab = Services.REGISTRY.createCreativeTab(Component.translatableWithFallback("itemGroup." + label, label),
                this::getIconItem, (params, output) -> output.acceptAll(this.getSortedStacks()));
    }

    /** Builds a tab titled with the given label (translated through {@code itemGroup.<label>} when available). */
    public static CreativeTab create(String label) {
        return new CreativeTab(label);
    }

    /** The vanilla tab to register and to compare against in tab events. */
    public CreativeModeTab getTab() {
        return this.tab;
    }

    /** Uses the named item (a bare name means the {@code mcheli} namespace) as a fixed icon; empty or unknown cycles. */
    public void setFixedIconItem(String itemName) {
        this.fixedIcon = null;
        if (itemName == null || itemName.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemName.indexOf(':') >= 0 ? itemName : Ids.MOD_ID + ":" + itemName);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            this.fixedIcon = new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
    }

    private ItemStack getIconItem() {
        if (this.fixedIcon != null) {
            return this.fixedIcon;
        }
        if (this.iconSwitchCountdown > 0) {
            this.iconSwitchCountdown--;
        } else {
            this.currentIcon = this.nextIcon();
            this.iconSwitchCountdown = ICON_SWITCH_DELAY;
        }
        if (this.currentIcon.isEmpty()) {
            this.currentIcon = new ItemStack(Items.IRON_BLOCK);
        }
        return this.currentIcon;
    }

    private ItemStack nextIcon() {
        if (this.iconStacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.currentIconIndex = (this.currentIconIndex + 1) % this.iconStacks.size();
        return this.iconStacks.get(this.currentIconIndex);
    }

    /** Adds the item to this tab's contents and icon rotation. */
    public void addIconItem(Item item) {
        if (item != null && !this.items.contains(item)) {
            this.items.add(item);
            this.iconStacks.add(new ItemStack(item));
        }
    }

    private List<ItemStack> getSortedStacks() {
        List<ItemStack> stacks = new ArrayList<>(this.items.size());
        for (Item item : this.items) {
            stacks.add(new ItemStack(item));
        }
        stacks.sort(DISPLAY_ORDER);
        return stacks;
    }
}
