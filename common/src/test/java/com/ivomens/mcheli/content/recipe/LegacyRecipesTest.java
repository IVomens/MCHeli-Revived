package com.ivomens.mcheli.content.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRecipesTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static Map<String, JsonElement> constants() {
        Map<String, JsonElement> c = new HashMap<>();
        c.put("IRON_INGOT", JsonParser.parseString("{\"type\":\"forge:ore_dict\",\"ore\":\"ingotIron\"}"));
        c.put("GLASS", JsonParser.parseString("{\"type\":\"forge:ore_dict\",\"ore\":\"blockGlass\"}"));
        return c;
    }

    @Test
    void convertsShapedOreRecipeWithMetadataAlternatives() throws Exception {
        JsonObject legacy = json("""
                {
                  "type": "forge:ore_shaped",
                  "pattern": ["ICI", "III"],
                  "key": {
                    "I": {"item": "#IRON_INGOT"},
                    "C": [{"item": "minecraft:coal", "data": 0}, {"item": "minecraft:coal", "data": 1}]
                  },
                  "result": {"item": "mcheli:fuel", "count": 1}
                }
                """);
        JsonObject out = LegacyRecipeConverter.convert(legacy, constants(), null);
        assertEquals("minecraft:crafting_shaped", out.get("type").getAsString());
        assertEquals("c:ingots/iron", out.getAsJsonObject("key").getAsJsonObject("I").get("tag").getAsString());
        var c = out.getAsJsonObject("key").getAsJsonArray("C");
        assertEquals("minecraft:coal", c.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:charcoal", c.get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("mcheli:fuel", out.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, out.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void convertsShapeless() throws Exception {
        JsonObject legacy = json("""
                {"type": "minecraft:crafting_shapeless",
                 "ingredients": [{"item": "minecraft:dye", "data": 4}, {"item": "#GLASS"}],
                 "result": {"item": "mcheli:thing", "count": 3}}
                """);
        JsonObject out = LegacyRecipeConverter.convert(legacy, constants(), null);
        assertEquals("minecraft:crafting_shapeless", out.get("type").getAsString());
        assertEquals("minecraft:lapis_lazuli", out.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("c:glass_blocks", out.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("tag").getAsString());
        assertEquals(3, out.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void rejectsUnknownItemsAndConstants() {
        JsonObject unknownItem = json("""
                {"type": "forge:ore_shaped", "pattern": ["X"], "key": {"X": {"item": "minecraft:stone"}},
                 "result": {"item": "someaddon:missing"}}
                """);
        assertThrows(RecipeConversionException.class,
                () -> LegacyRecipeConverter.convert(unknownItem, constants(), rl -> !rl.getNamespace().equals("someaddon")));

        JsonObject unknownConstant = json("""
                {"type": "forge:ore_shaped", "pattern": ["X"], "key": {"X": {"item": "#NOPE"}},
                 "result": {"item": "minecraft:stone"}}
                """);
        assertThrows(RecipeConversionException.class,
                () -> LegacyRecipeConverter.convert(unknownConstant, constants(), null));

        JsonObject undefinedKey = json("""
                {"type": "forge:ore_shaped", "pattern": ["XY"], "key": {"X": {"item": "minecraft:stone"}},
                 "result": {"item": "minecraft:stone"}}
                """);
        assertThrows(RecipeConversionException.class,
                () -> LegacyRecipeConverter.convert(undefinedKey, constants(), null));
    }

    @Test
    void legacyItemIds() {
        assertEquals("minecraft:white_wool", LegacyItemIds.legacyItemId("wool", 0));
        assertEquals("minecraft:red_wool", LegacyItemIds.legacyItemId("minecraft:wool", 14));
        assertEquals("minecraft:sugar_cane", LegacyItemIds.legacyItemId("reeds", 0));
        assertEquals("minecraft:iron_ingot", LegacyItemIds.legacyItemId("iron_ingot", 0));
        assertEquals("mcheli:fuel", LegacyItemIds.legacyItemId("mcheli:fuel", 3));
        assertEquals("minecraft:bone_meal", LegacyItemIds.legacyItemId("dye", 15));
    }

    @Test
    void oreDictionaryToTags() {
        assertEquals("minecraft:planks", LegacyItemIds.oreToTag("plankWood"));
        assertEquals("c:ingots/iron", LegacyItemIds.oreToTag("ingotIron"));
        assertEquals("c:storage_blocks/redstone", LegacyItemIds.oreToTag("blockRedstone"));
        assertEquals("c:dusts/redstone", LegacyItemIds.oreToTag("dustRedstone"));
        assertEquals("c:gems/diamond", LegacyItemIds.oreToTag("gemDiamond"));
        assertEquals("c:ingots/stainless_steel", LegacyItemIds.oreToTag("ingotStainlessSteel"));
        assertTrue(LegacyItemIds.oreToTag("egg").startsWith("c:"));
    }
}
