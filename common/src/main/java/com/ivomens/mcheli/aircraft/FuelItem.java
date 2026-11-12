package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Fuel can. Its damage value is the amount of fuel missing; right-click refills it from charcoal
 * and coal in the player's inventory.
 */
public class FuelItem extends McheliItem {

    public static final int MAX_FUEL = 600;
    private static final int FUEL_PER_CHARCOAL = 75;
    private static final int FUEL_PER_COAL = 100;
    /** At most this many items of one stack are used per click. */
    private static final int MAX_ITEMS_PER_STACK = 64;

    public FuelItem(Item.Properties properties) {
        super(Services.GAMEPLAY.noRepair(properties.durability(MAX_FUEL)));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !stack.isDamaged() || player.getAbilities().instabuild) {
            return InteractionResultHolder.pass(stack);
        }
        refill(stack, player, Items.CHARCOAL, FUEL_PER_CHARCOAL);
        refill(stack, player, Items.COAL, FUEL_PER_COAL);
        return InteractionResultHolder.success(stack);
    }

    private static void refill(ItemStack can, Player player, Item fuelItem, int fuelPerItem) {
        NonNullList<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack fuel = items.get(i);
            if (fuel.isEmpty() || fuel.getItem() != fuelItem) {
                continue;
            }
            for (int used = 0; fuel.getCount() > 0 && can.isDamaged() && used < MAX_ITEMS_PER_STACK; used++) {
                can.setDamageValue(Math.max(can.getDamageValue() - fuelPerItem, 0));
                fuel.shrink(1);
            }
            if (fuel.getCount() <= 0) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
