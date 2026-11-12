package com.ivomens.mcheli.content.recipe;

import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the recipe strings of content files into generated recipes. A shaped recipe string is
 * {@code [count,] "ROW", ["ROW", ["ROW",]] K, item[, meta], K, item[, meta]...}; a shapeless one is
 * {@code item[, meta], item[, meta]...}. Items are 1.12 names, optionally with a metadata number.
 * <p>
 * A string that cannot be read is skipped and logged with the content name and the reason; keys that do not
 * appear in the pattern are skipped with a warning.
 */
final class RecipeStrings {

    private static final int PATTERN_ROWS = 3;
    /** The pattern and one key with its item. */
    private static final int MIN_SHAPED_ELEMENTS = 3;

    private final String name;
    private final String data;
    private final String[] elements;
    /** Pattern rows, then key characters and item ids, as {@link GeneratedRecipes#addShaped} takes them. */
    private final List<Object> recipe = new ArrayList<>();

    private RecipeStrings(String name, String data) {
        this.name = name;
        this.data = data;
        this.elements = data.split("\\s*,\\s*");
    }

    /** Adds the shaped recipe {@code data} makes {@code item} with; null when it is skipped. */
    @Nullable
    static ResourceLocation addShaped(String name, @Nullable Item item, String data) {
        RecipeStrings strings = new RecipeStrings(name, data);
        if (item == null) {
            strings.warn("the result item does not exist");
            return null;
        }
        return strings.shaped(item);
    }

    /** Adds the shapeless recipe {@code data} makes {@code item} with; null when it is skipped. */
    @Nullable
    static ResourceLocation addShapeless(String name, @Nullable Item item, String data) {
        RecipeStrings strings = new RecipeStrings(name, data);
        if (item == null) {
            strings.warn("the result item does not exist");
            return null;
        }
        return strings.shapeless(item);
    }

    @Nullable
    private ResourceLocation shaped(Item item) {
        if (this.elements.length < MIN_SHAPED_ELEMENTS) {
            warn("too few elements");
            return null;
        }
        int start = isNumber(this.elements[0]) ? 1 : 0;
        int resultCount = start == 1 ? Math.max(1, Integer.parseInt(this.elements[0])) : 1;
        Set<Integer> patternChars = new HashSet<>();
        int keysStart = readPattern(start, patternChars);
        if (keysStart == start) {
            warn("no pattern rows");
            return null;
        }
        if (!readKeys(keysStart, patternChars)) {
            return null;
        }
        try {
            return GeneratedRecipes.addShaped(this.name, new ItemStack(item, resultCount), this.recipe.toArray());
        } catch (Exception e) {
            warn(e.getMessage());
            return null;
        }
    }

    /** Reads up to three quoted rows from {@code start}; returns the index after them. */
    private int readPattern(int start, Set<Integer> patternChars) {
        int index = start;
        for (int row = 0; row < PATTERN_ROWS && index < this.elements.length; row++) {
            String element = this.elements[index];
            if (element.length() >= 2 && element.charAt(0) == '"' && element.charAt(element.length() - 1) == '"') {
                String pattern = element.substring(1, element.length() - 1).toUpperCase(Locale.ROOT);
                pattern.chars().forEach(patternChars::add);
                this.recipe.add(pattern);
                index++;
            }
        }
        return index;
    }

    /** Reads the key letters and their items from {@code start}; false (after logging why) for a bad one. */
    private boolean readKeys(int start, Set<Integer> patternChars) {
        boolean expectKey = true;
        boolean skipIngredient = false;
        for (int index = start; index < this.elements.length; index++) {
            if (this.elements[index].isEmpty()) {
                warn("empty element");
                return false;
            }
            if (expectKey) {
                Character key = readKey(this.elements[index]);
                if (key == null) {
                    return false;
                }
                if (!patternChars.contains((int) key)) {
                    ModLogger.get().warn("Key defines symbols that aren't used in pattern: [{}], item:{}", key,
                            this.name);
                    skipIngredient = true;
                } else {
                    this.recipe.add(key);
                }
            } else {
                if (!skipIngredient) {
                    index = readIngredient(index);
                    if (index < 0) {
                        return false;
                    }
                } else if (index + 1 < this.elements.length && isNumber(this.elements[index + 1])) {
                    index++;
                }
                skipIngredient = false;
            }
            expectKey = !expectKey;
        }
        return true;
    }

    /** A key: one letter, upper-cased; null (after logging why) for anything else. */
    @Nullable
    private Character readKey(String element) {
        if (element.length() != 1) {
            warn("key '" + element + "' is not a single letter");
            return null;
        }
        char key = element.toUpperCase(Locale.ROOT).charAt(0);
        if (key < 'A' || key > 'Z') {
            warn("key '" + key + "' is not a letter");
            return null;
        }
        return key;
    }

    @Nullable
    private ResourceLocation shapeless(Item item) {
        for (int index = 0; index < this.elements.length; index++) {
            if (this.elements[index].isEmpty()) {
                warn("empty element");
                return null;
            }
            index = readIngredient(index);
            if (index < 0) {
                return null;
            }
        }
        try {
            return GeneratedRecipes.addShapeless(this.name, new ItemStack(item, 1), this.recipe.toArray());
        } catch (Exception e) {
            warn(e.getMessage());
            return null;
        }
    }

    /**
     * Reads the ingredient at {@code index} (optionally followed by a legacy metadata number) and adds its item
     * id to the recipe. Returns the index of the last element consumed, or -1 (after logging why) if the
     * ingredient is invalid.
     */
    private int readIngredient(int index) {
        String itemName = this.elements[index].trim().toLowerCase(Locale.ROOT);
        int last = index;
        int meta = 0;
        if (last + 1 < this.elements.length && isNumber(this.elements[last + 1])) {
            meta = Integer.parseInt(this.elements[++last]);
        }
        if (isNumber(itemName)) {
            warn("numeric item ids are not supported (" + itemName + ")");
            return -1;
        }
        ResourceLocation id = ResourceLocation.tryParse(LegacyItemIds.legacyItemId(itemName, meta));
        if (id == null || !LegacyItemIds.isRegistered(id)) {
            warn("unknown item '" + itemName + (meta != 0 ? ":" + meta : "") + "'");
            return -1;
        }
        this.recipe.add(id.toString());
        return last;
    }

    private void warn(String reason) {
        ModLogger.get().warn("Recipe of {} skipped: {} [{}]", this.name, reason, this.data);
    }

    private static boolean isNumber(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
