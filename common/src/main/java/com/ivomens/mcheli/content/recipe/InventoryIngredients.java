package com.ivomens.mcheli.content.recipe;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Crafting from the player's main inventory, as the drafting table does: every ingredient takes one item,
 * so a recipe that needs four ingredients of one kind needs four items.
 */
public final class InventoryIngredients {

    private InventoryIngredients() {
    }

    /** Whether the player's main inventory holds every ingredient of the recipe, counting each item once. */
    public static boolean canCraft(Player player, Recipe<?> recipe) {
        return plan(player, recipe) != null;
    }

    /**
     * Removes the recipe's ingredients from the player's main inventory, one item per ingredient.
     * Nothing is taken unless all of them are there.
     */
    public static boolean consume(Player player, Recipe<?> recipe) {
        int[] taken = plan(player, recipe);
        if (taken == null) {
            return false;
        }
        for (int slot = 0; slot < taken.length; slot++) {
            if (taken[slot] > 0) {
                player.getInventory().removeItem(slot, taken[slot]);
            }
        }
        return true;
    }

    /** How many items to take from each main inventory slot to cover every ingredient, or null when one is missing. */
    @Nullable
    private static int[] plan(Player player, Recipe<?> recipe) {
        List<ItemStack> items = player.getInventory().items;
        int[] taken = new int[items.size()];
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            int slot = 0;
            while (slot < items.size()
                    && !(taken[slot] < items.get(slot).getCount() && ingredient.test(items.get(slot)))) {
                slot++;
            }
            if (slot == items.size()) {
                return null;
            }
            taken[slot]++;
        }
        return taken;
    }
}
