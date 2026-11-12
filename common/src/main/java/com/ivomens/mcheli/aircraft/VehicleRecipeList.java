package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The drafting table page of one vehicle kind: the recipes of its vehicles' items. */
public final class VehicleRecipeList implements ContentRecipes.RecipePage {

    private static final Map<VehicleKind, VehicleRecipeList> PAGES = pages();

    private final VehicleKind kind;
    private final List<RecipeHolder<?>> recipes = new ArrayList<>();

    private VehicleRecipeList(VehicleKind kind) {
        this.kind = kind;
    }

    private static Map<VehicleKind, VehicleRecipeList> pages() {
        Map<VehicleKind, VehicleRecipeList> pages = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            pages.put(kind, new VehicleRecipeList(kind));
        }
        return pages;
    }

    public static VehicleRecipeList of(VehicleKind kind) {
        return PAGES.get(kind);
    }

    public VehicleKind kind() {
        return this.kind;
    }

    @Override
    public int getRecipeListSize() {
        return this.recipes.size();
    }

    @Override
    public RecipeHolder<?> getRecipe(int index) {
        return this.recipes.get(index);
    }

    /** Recipes are rebuilt from the server's recipe manager on every data pack (re)load. */
    @Override
    public void clearRecipes() {
        this.recipes.clear();
    }

    @Override
    public void addRecipe(RecipeHolder<?> recipe, int index, String name, String recipeString) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe " + index + " of '" + name + "' could not be built: "
                    + recipeString);
        }
        this.recipes.add(recipe);
    }

    /** The vehicle the recipe makes. */
    public Optional<VehicleSpec> vehicleMadeBy(@Nullable RecipeHolder<?> recipe) {
        if (recipe == null) {
            return Optional.empty();
        }
        return VehicleTypes.fromItem(this.kind, recipe.value().getResultItem(RegistryAccess.EMPTY).getItem());
    }
}
