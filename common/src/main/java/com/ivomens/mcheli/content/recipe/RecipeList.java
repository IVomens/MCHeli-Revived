package com.ivomens.mcheli.content.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

/** A list of recipes shown on one page of the drafting table. */
public interface RecipeList {

    int getRecipeListSize();

    RecipeHolder<?> getRecipe(int index);
}
