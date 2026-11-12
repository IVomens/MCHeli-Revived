package com.ivomens.mcheli.aircraft;

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

/** Refills a partly used fuel can with coal (100 each) or charcoal (75 each). */
public class FuelRefillRecipe extends CustomRecipe {

    public static final Supplier<SimpleCraftingRecipeSerializer<FuelRefillRecipe>> SERIALIZER =
            Services.REGISTRY.register(Registries.RECIPE_SERIALIZER, "charge_fuel",
                    () -> new SimpleCraftingRecipeSerializer<>(FuelRefillRecipe::new));

    private static final int COAL_FUEL = 100;
    private static final int CHARCOAL_FUEL = 75;

    /** Loading the class registers the serializer. */
    public static void register() {
    }

    public FuelRefillRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean isFuelSource(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    /** Exactly one partly used fuel can plus at least one coal or charcoal, nothing else. */
    @Override
    public boolean matches(CraftingInput input, @NotNull Level level) {
        int cans = 0;
        int fuelSources = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof FuelItem) {
                if (stack.getDamageValue() == 0 || ++cans > 1) {
                    return false;
                }
            } else if (isFuelSource(stack)) {
                fuelSources++;
            } else {
                return false;
            }
        }
        return cans == 1 && fuelSources > 0;
    }

    @Override
    public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.@NotNull Provider registries) {
        int usedFuel = 0;
        int refill = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof FuelItem) {
                usedFuel = stack.getDamageValue();
            } else if (isFuelSource(stack)) {
                refill += stack.is(Items.CHARCOAL) ? CHARCOAL_FUEL : COAL_FUEL;
            }
        }
        ItemStack output = new ItemStack(McheliItems.FUEL.get());
        output.setDamageValue(Math.max(usedFuel - refill, 0));
        return output;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }
}
