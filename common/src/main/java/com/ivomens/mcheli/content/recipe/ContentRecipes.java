package com.ivomens.mcheli.content.recipe;

import com.google.common.collect.Sets;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Recipes shown on the drafting table.
 * <p>
 * Stock item recipes are static data pack JSON ({@code data/mcheli/recipe}). Vehicle and throwable
 * recipes are defined in the content files; {@link #registerItemRecipes} turns their recipe strings
 * into generated recipe JSON (served by the generated recipe data pack, see {@link GeneratedRecipes}).
 * Every recipe that references an item which does not exist is skipped and logged with the content
 * name and the missing item.
 * <p>
 * Whenever the recipe manager is (re)loaded on either side, {@link #resolve(RecipeManager)} rebuilds
 * the drafting table lists from it.
 */
public class ContentRecipes implements RecipeList {

    /** Stock recipes listed on the "items" page, in display order. */
    private static final String[] COMMON_RECIPES = {
            "fuel", "gltd", "chain", "parachute", "container", "uav_station", "uav_station2", "wrench",
            "range_finder", "fim92", "fim92_bullet", "fgm148", "fgm148_bullet", "spawn_gunner_vs_monster",
            "spawn_gunner_vs_player", "drafting_table"
    };

    private static final ContentRecipes INSTANCE = new ContentRecipes();
    /** A recipe string needs at least this many characters to describe anything. */
    private static final int MIN_RECIPE_LENGTH = 3;

    /** The drafting table's page of common items, rebuilt after each recipe reload. */
    private volatile List<RecipeHolder<?>> commonItemRecipe = Collections.emptyList();

    /**
     * A content file whose recipe strings make its item.
     *
     * @param item          the item made, or null when it does not exist (its recipes are skipped)
     * @param recipeStrings the file's recipe strings, in file order
     * @param page          the drafting table page of a vehicle, null for throwables (which go on the items page)
     */
    public record RecipeSource(String name, @Nullable Item item, List<String> recipeStrings, boolean shaped,
                               @Nullable RecipePage page) {
    }

    /** A drafting table page that lists the recipes of content files. */
    public interface RecipePage extends RecipeList {

        void clearRecipes();

        /** @param index 1-based position of the recipe string in the content file */
        void addRecipe(RecipeHolder<?> recipe, int index, String name, String recipeString);
    }

    /**
     * A content recipe generated from a recipe string, resolved to a holder after each recipe reload.
     *
     * @param index 1-based position of the recipe string in the content file
     */
    private record Generated(String name, @Nullable RecipePage page, ResourceLocation id, int index,
                             String recipeString) {
    }

    private static final List<Generated> generated = new ArrayList<>();

    private ContentRecipes() {
    }

    public static ContentRecipes getInstance() {
        return INSTANCE;
    }

    /** Generates the recipes of the given content files, in order. Call once all items are registered. */
    public static synchronized void registerItemRecipes(List<RecipeSource> sources) {
        for (Generated recipe : generated) {
            GeneratedRecipes.unregister(recipe.id());
        }
        generated.clear();
        sources.forEach(ContentRecipes::addContentRecipes);
    }

    private static void addContentRecipes(RecipeSource source) {
        int index = 0;
        for (String recipeString : source.recipeStrings()) {
            index++;
            if (recipeString.length() < MIN_RECIPE_LENGTH) {
                continue;
            }
            ResourceLocation id = source.shaped() ? RecipeStrings.addShaped(source.name(), source.item(), recipeString)
                    : RecipeStrings.addShapeless(source.name(), source.item(), recipeString);
            if (id != null) {
                generated.add(new Generated(source.name(), source.page(), id, index, recipeString));
            }
        }
    }

    /** Rebuilds the drafting table lists from the current recipes. */
    public static synchronized void resolve(RecipeManager recipeManager) {
        List<RecipeHolder<?>> common = new ArrayList<>();
        for (String name : COMMON_RECIPES) {
            recipeManager.byKey(Ids.of(name)).ifPresent(common::add);
        }

        Set<RecipePage> clearedPages = Sets.newIdentityHashSet();
        for (Generated recipe : generated) {
            if (recipe.page() != null && clearedPages.add(recipe.page())) {
                recipe.page().clearRecipes();
            }
        }
        for (Generated recipe : generated) {
            RecipeHolder<?> holder = recipeManager.byKey(recipe.id()).orElse(null);
            if (holder == null) {
                continue;
            }
            if (recipe.page() != null) {
                recipe.page().addRecipe(holder, recipe.index(), recipe.name(), recipe.recipeString());
            } else {
                common.add(holder);
            }
        }
        INSTANCE.commonItemRecipe = Collections.unmodifiableList(common);
    }

    @Override
    public int getRecipeListSize() {
        return this.commonItemRecipe.size();
    }

    @Override
    public RecipeHolder<?> getRecipe(int index) {
        return this.commonItemRecipe.get(index);
    }
}
