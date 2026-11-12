package com.ivomens.mcheli.equipment.rangefinder;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** Recharges a used rangefinder with one redstone dust. */
public class RangeFinderRechargeRecipe extends CustomRecipe {

    public static final Supplier<SimpleCraftingRecipeSerializer<RangeFinderRechargeRecipe>> SERIALIZER =
            Services.REGISTRY.register(Registries.RECIPE_SERIALIZER, "charge_power_range_finder",
                    () -> new SimpleCraftingRecipeSerializer<>(RangeFinderRechargeRecipe::new));

    /** Loading the class registers the serializer. */
    public static void register() {
    }

    public RangeFinderRechargeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** Exactly one used range finder and one redstone dust, nothing else. */
    @Override
    public boolean matches(CraftingInput input, @NotNull Level level) {
        int rangeFinders = 0;
        int redstone = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof RangeFinderItem) {
                if (stack.getDamageValue() == 0 || ++rangeFinders > 1) {
                    return false;
                }
            } else if (!stack.is(Items.REDSTONE) || ++redstone > 1) {
                return false;
            }
        }
        return rangeFinders == 1 && redstone == 1;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
        return new ItemStack(McheliItems.RANGEFINDER.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 2 && height >= 2;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }
}
