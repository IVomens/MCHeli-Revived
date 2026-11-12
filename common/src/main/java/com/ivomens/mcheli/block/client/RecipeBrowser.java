package com.ivomens.mcheli.block.client;

import com.ivomens.mcheli.content.recipe.RecipeList;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.ScrollBar;
import com.ivomens.mcheli.ui.client.Widgets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The drafting table's list page: the recipes of one list, two per row and three rows at a time, each
 * with its name, its ingredients and a Select button, and the mod's scroll bar on the right.
 */
final class RecipeBrowser {

    private static final int COLUMNS = 2;
    private static final int ROWS = 3;
    private static final int SLOTS = COLUMNS * ROWS;
    private static final int COLUMN_WIDTH = 140;
    private static final int ROW_HEIGHT = 70;
    private static final int CARD_U = 400;
    private static final int CARD_WIDTH = 75;
    private static final int CARD_HEIGHT = 54;
    private static final int GRID_SLOT = 18;
    private static final int TEXTURE_SIZE = 512;

    private final IntConsumer onSelect;
    private final List<Button> selectButtons = new ArrayList<>();
    private RecipeList list;
    private ScrollBar bar;

    /** @param onSelect receives the index in the list of the recipe whose Select button was pressed */
    RecipeBrowser(RecipeList list, IntConsumer onSelect) {
        this.list = list;
        this.onSelect = onSelect;
    }

    /** Adds the Select buttons and the scroll bar of the page whose panel starts at {@code (left, top)}. */
    void build(Consumer<AbstractWidget> add, int left, int top) {
        int scroll = this.bar == null ? 0 : this.bar.position();
        this.selectButtons.clear();
        Layout grid = Layout.grid(left + 30, top + 40, COLUMNS, COLUMN_WIDTH, ROW_HEIGHT, add);
        for (int slot = 0; slot < SLOTS; slot++) {
            int offset = slot;
            this.selectButtons.add(grid.add(Widgets.button(Component.translatable("mcheli.drafting.select"), 45, 20,
                    () -> this.onSelect.accept(this.first() + offset))));
        }
        this.bar = Widgets.scrollBar(20, 200, position -> this.refresh());
        this.bar.setPosition(left + 360, top + 20);
        add.accept(this.bar);
        this.bar.setMax(this.maxScroll());
        this.bar.setPosition(scroll);
    }

    RecipeList list() {
        return this.list;
    }

    /** Shows another list from its top. */
    void setList(RecipeList list) {
        this.list = list;
        this.bar.setMax(this.maxScroll());
        this.bar.setPosition(0);
        this.refresh();
    }

    /** Scrolls so the row of the recipe at {@code index} is on top. */
    void scrollTo(int index) {
        this.bar.setPosition(Math.round(index / (float) COLUMNS));
    }

    /** Scrolls by whole rows; positive goes down the list. */
    void scroll(int rows) {
        this.bar.setPosition(this.bar.position() + rows);
    }

    private int maxScroll() {
        int rows = (this.list.getRecipeListSize() + 1) / COLUMNS;
        return Math.max(0, rows - ROWS);
    }

    private int first() {
        return this.bar.position() * COLUMNS;
    }

    /** Shows the page's widgets, with Select only on the slots that hold a recipe. */
    void setVisible(boolean visible) {
        this.bar.visible = visible;
        for (int slot = 0; slot < this.selectButtons.size(); slot++) {
            this.selectButtons.get(slot).visible = visible && this.first() + slot < this.list.getRecipeListSize();
        }
    }

    private void refresh() {
        this.setVisible(this.bar.visible);
    }

    /** Draws the page's cards, relative to the panel's top-left corner. */
    void renderCards(GuiGraphics g, Font font, ResourceLocation texture) {
        for (int slot = 0; slot < SLOTS; slot++) {
            int index = this.first() + slot;
            if (index >= this.list.getRecipeListSize()) {
                break;
            }
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            RecipeHolder<?> recipe = this.list.getRecipe(index);
            g.drawCenteredString(font, CurrentRecipe.getOutput(recipe).getHoverName(), 110 + COLUMN_WIDTH * column,
                    20 + ROW_HEIGHT * row, -1);
            int x = 80 + COLUMN_WIDTH * column;
            int y = 30 + ROW_HEIGHT * row;
            g.blit(texture, x - 1, y - 1, CARD_WIDTH, CARD_HEIGHT, CARD_U, 0, CARD_WIDTH, CARD_HEIGHT, TEXTURE_SIZE,
                    TEXTURE_SIZE);
            drawRecipe(g, font, recipe, x, y);
        }
    }

    /** A recipe as a crafting grid with its output to the right. */
    static void drawRecipe(GuiGraphics g, Font font, @Nullable RecipeHolder<?> recipe, int x, int y) {
        ItemStack output = CurrentRecipe.getOutput(recipe);
        if (recipe == null || output.isEmpty()) {
            return;
        }
        List<Ingredient> ingredients = recipe.value().getIngredients();
        int width = recipe.value() instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            drawIngredient(g, ingredients.get(i), x + i % width * GRID_SLOT, y + i / width * GRID_SLOT);
        }
        g.renderItem(output, x + 3 * GRID_SLOT + 3, y + GRID_SLOT);
        g.renderItemDecorations(font, output, x + 3 * GRID_SLOT + 3, y + GRID_SLOT);
    }

    private static void drawIngredient(GuiGraphics g, Ingredient ingredient, int x, int y) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length > 0) {
            // Cycle through the alternatives of a tag once per second.
            g.renderItem(stacks[(int) ((System.currentTimeMillis() / 1000L) % stacks.length)], x, y);
        }
    }

    /** The recipes of several lists whose output's name contains a text, ignoring case. */
    static final class Filtered implements RecipeList {

        private final List<RecipeHolder<?>> recipes = new ArrayList<>();

        Filtered(List<RecipeList> lists, String text) {
            String needle = text.toLowerCase(Locale.ROOT);
            for (RecipeList list : lists) {
                for (int i = 0; i < list.getRecipeListSize(); i++) {
                    RecipeHolder<?> recipe = list.getRecipe(i);
                    ItemStack output = CurrentRecipe.getOutput(recipe);
                    if (!output.isEmpty() && output.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                        this.recipes.add(recipe);
                    }
                }
            }
        }

        @Override
        public int getRecipeListSize() {
            return this.recipes.size();
        }

        @Override
        public RecipeHolder<?> getRecipe(int index) {
            return this.recipes.get(index);
        }
    }
}
