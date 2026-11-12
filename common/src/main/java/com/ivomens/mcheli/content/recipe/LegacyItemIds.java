package com.ivomens.mcheli.content.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Item names of Minecraft 1.12, as old recipe strings and recipe JSON spell them, mapped to 1.21: ore
 * dictionary names become common tags, and items the flattening renamed or split by metadata get their new
 * ids.
 */
public final class LegacyItemIds {

    private static final Map<String, String> ORE_TAGS = new HashMap<>();
    private static final String[][] ORE_PREFIXES = {
            { "ingot", "c:ingots/" }, { "gem", "c:gems/" }, { "dust", "c:dusts/" }, { "nugget", "c:nuggets/" },
            { "block", "c:storage_blocks/" }, { "ore", "c:ores/" }, { "plate", "c:plates/" }, { "gear", "c:gears/" },
            { "rod", "c:rods/" }, { "stick", "c:rods/" }, { "crop", "c:crops/" }, { "dye", "c:dyes/" },
            { "raw", "c:raw_materials/" }
    };

    static {
        ORE_TAGS.put("plankWood", "minecraft:planks");
        ORE_TAGS.put("logWood", "minecraft:logs");
        ORE_TAGS.put("slabWood", "minecraft:wooden_slabs");
        ORE_TAGS.put("stairWood", "minecraft:wooden_stairs");
        ORE_TAGS.put("fenceWood", "c:fences/wooden");
        ORE_TAGS.put("fenceGateWood", "c:fence_gates/wooden");
        ORE_TAGS.put("stickWood", "c:rods/wooden");
        ORE_TAGS.put("treeSapling", "minecraft:saplings");
        ORE_TAGS.put("treeLeaves", "minecraft:leaves");
        ORE_TAGS.put("blockGlass", "c:glass_blocks");
        ORE_TAGS.put("blockGlassColorless", "c:glass_blocks/colorless");
        ORE_TAGS.put("paneGlass", "c:glass_panes");
        ORE_TAGS.put("paneGlassColorless", "c:glass_panes/colorless");
        ORE_TAGS.put("wool", "minecraft:wool");
        ORE_TAGS.put("blockWool", "minecraft:wool");
        ORE_TAGS.put("string", "c:strings");
        ORE_TAGS.put("gunpowder", "c:gunpowders");
        ORE_TAGS.put("egg", "c:eggs");
        ORE_TAGS.put("workbench", "c:player_workstations/crafting_tables");
        ORE_TAGS.put("chest", "c:chests");
        ORE_TAGS.put("chestWood", "c:chests/wooden");
        ORE_TAGS.put("chestEnder", "c:chests/ender");
        ORE_TAGS.put("chestTrapped", "c:chests/trapped");
        ORE_TAGS.put("stone", "c:stones");
        ORE_TAGS.put("cobblestone", "c:cobblestones");
        ORE_TAGS.put("sand", "c:sands");
        ORE_TAGS.put("sandstone", "c:sandstone/blocks");
        ORE_TAGS.put("gravel", "c:gravels");
        ORE_TAGS.put("netherrack", "c:netherracks");
        ORE_TAGS.put("obsidian", "c:obsidians");
        ORE_TAGS.put("leather", "c:leathers");
        ORE_TAGS.put("feather", "c:feathers");
        ORE_TAGS.put("bone", "c:bones");
        ORE_TAGS.put("slimeball", "c:slime_balls");
        ORE_TAGS.put("enderpearl", "c:ender_pearls");
        ORE_TAGS.put("netherStar", "c:nether_stars");
        ORE_TAGS.put("record", "c:music_discs");
        ORE_TAGS.put("blockPrismarine", "minecraft:prismarine");
        ORE_TAGS.put("glowstone", "c:dusts/glowstone");
        ORE_TAGS.put("torch", "minecraft:torch");
        ORE_TAGS.put("vine", "minecraft:vine");
        ORE_TAGS.put("cropWheat", "c:crops/wheat");
        ORE_TAGS.put("cropPotato", "c:crops/potato");
        ORE_TAGS.put("cropCarrot", "c:crops/carrot");
        ORE_TAGS.put("sugarcane", "c:crops/sugar_cane");
        ORE_TAGS.put("gemLapis", "c:gems/lapis");
        ORE_TAGS.put("gemQuartz", "c:gems/quartz");
        ORE_TAGS.put("gemPrismarine", "c:gems/prismarine");
        ORE_TAGS.put("dustPrismarine", "c:gems/prismarine");
    }

    /**
     * Maps a 1.12 ore dictionary name to a 1.21 tag id (without '#').
     */
    public static String oreToTag(String ore) {
        String mapped = ORE_TAGS.get(ore);
        if (mapped != null) {
            return mapped;
        }
        for (String[] prefix : ORE_PREFIXES) {
            if (ore.startsWith(prefix[0]) && ore.length() > prefix[0].length() && Character.isUpperCase(ore.charAt(prefix[0].length()))) {
                return prefix[1] + camelToSnake(ore.substring(prefix[0].length()));
            }
        }
        return "c:" + camelToSnake(ore);
    }

    private static String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final Map<String, String> RENAMED = new HashMap<>();
    private static final Map<String, String[]> META_VARIANTS = new HashMap<>();

    static {
        String[][] renamed = {
                { "boat", "oak_boat" }, { "reeds", "sugar_cane" }, { "fence", "oak_fence" },
                { "fence_gate", "oak_fence_gate" }, { "wooden_door", "oak_door" }, { "trapdoor", "oak_trapdoor" },
                { "wooden_button", "oak_button" }, { "wooden_pressure_plate", "oak_pressure_plate" },
                { "noteblock", "note_block" }, { "web", "cobweb" }, { "speckled_melon", "glistering_melon_slice" },
                { "melon", "melon_slice" }, { "melon_block", "melon" }, { "lit_pumpkin", "jack_o_lantern" },
                { "snow_layer", "snow" }, { "snow", "snow_block" }, { "brick_block", "bricks" },
                { "nether_brick", "nether_bricks" }, { "netherbrick", "nether_brick" }, { "quartz_ore", "nether_quartz_ore" },
                { "stonebrick", "stone_bricks" }, { "fish", "cod" }, { "cooked_fish", "cooked_cod" },
                { "red_flower", "poppy" }, { "yellow_flower", "dandelion" }, { "tallgrass", "short_grass" },
                { "deadbush", "dead_bush" }, { "waterlily", "lily_pad" }, { "mob_spawner", "spawner" },
                { "hardened_clay", "terracotta" }, { "magma", "magma_block" }, { "slime", "slime_block" },
                { "end_bricks", "end_stone_bricks" }, { "chorus_fruit_popped", "popped_chorus_fruit" },
                { "firework_charge", "firework_star" }, { "fireworks", "firework_rocket" }, { "sign", "oak_sign" },
                { "grass", "grass_block" }, { "golden_rail", "powered_rail" }, { "stone_slab", "smooth_stone_slab" },
                { "wooden_slab", "oak_slab" }, { "skull", "skeleton_skull" }, { "lit_redstone_lamp", "redstone_lamp" },
                { "redstone_lamp", "redstone_lamp" }, { "crafting_table", "crafting_table" }, { "bed", "red_bed" },
                { "banner", "white_banner" }, { "carpet", "white_carpet" }, { "wool", "white_wool" },
                { "stained_glass", "white_stained_glass" }, { "stained_glass_pane", "white_stained_glass_pane" },
                { "stained_hardened_clay", "white_terracotta" }, { "concrete", "white_concrete" },
                { "concrete_powder", "white_concrete_powder" }, { "dye", "ink_sac" }, { "log", "oak_log" },
                { "log2", "acacia_log" }, { "planks", "oak_planks" }, { "sapling", "oak_sapling" },
                { "leaves", "oak_leaves" }, { "leaves2", "acacia_leaves" }, { "double_plant", "sunflower" },
                { "monster_egg", "infested_stone" }, { "silver_shulker_box", "light_gray_shulker_box" },
                { "silver_glazed_terracotta", "light_gray_glazed_terracotta" }, { "piston_extension", "moving_piston" },
                { "standing_banner", "white_banner" }, { "wall_banner", "white_wall_banner" }, { "chain_command_block", "chain_command_block" },
                { "record_13", "music_disc_13" }, { "record_cat", "music_disc_cat" }, { "record_blocks", "music_disc_blocks" },
                { "record_chirp", "music_disc_chirp" }, { "record_far", "music_disc_far" }, { "record_mall", "music_disc_mall" },
                { "record_mellohi", "music_disc_mellohi" }, { "record_stal", "music_disc_stal" }, { "record_strad", "music_disc_strad" },
                { "record_ward", "music_disc_ward" }, { "record_11", "music_disc_11" }, { "record_wait", "music_disc_wait" },
                { "spawn_egg", "pig_spawn_egg" }, { "golden_apple", "golden_apple" }, { "coal", "coal" },
                { "quartz_block", "quartz_block" }, { "red_sandstone", "red_sandstone" }, { "sandstone", "sandstone" },
                { "stone", "stone" }, { "dirt", "dirt" }, { "sand", "sand" }, { "sponge", "sponge" },
                { "prismarine", "prismarine" }, { "anvil", "anvil" }, { "cobblestone_wall", "cobblestone_wall" },
                { "flower_pot", "flower_pot" }, { "purpur_slab", "purpur_slab" }, { "stone_slab2", "red_sandstone_slab" }
        };
        for (String[] r : renamed) {
            RENAMED.put(r[0], r[1]);
        }

        putColored("wool", "_wool");
        putColored("carpet", "_carpet");
        putColored("stained_glass", "_stained_glass");
        putColored("stained_glass_pane", "_stained_glass_pane");
        putColored("stained_hardened_clay", "_terracotta");
        putColored("concrete", "_concrete");
        putColored("concrete_powder", "_concrete_powder");
        putColored("bed", "_bed");
        putColoredReversed("banner", "_banner");
        String[] dyeItems = new String[16];
        for (int i = 0; i < 16; i++) {
            String c = COLORS[15 - i];
            dyeItems[i] = switch (i) {
                case 0 -> "ink_sac";
                case 3 -> "cocoa_beans";
                case 4 -> "lapis_lazuli";
                case 15 -> "bone_meal";
                default -> c + "_dye";
            };
        }
        META_VARIANTS.put("dye", dyeItems);
        META_VARIANTS.put("coal", new String[] { "coal", "charcoal" });
        META_VARIANTS.put("golden_apple", new String[] { "golden_apple", "enchanted_golden_apple" });
        META_VARIANTS.put("planks", new String[] { "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks" });
        META_VARIANTS.put("log", new String[] { "oak_log", "spruce_log", "birch_log", "jungle_log" });
        META_VARIANTS.put("log2", new String[] { "acacia_log", "dark_oak_log" });
        META_VARIANTS.put("sapling", new String[] { "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling" });
        META_VARIANTS.put("leaves", new String[] { "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves" });
        META_VARIANTS.put("leaves2", new String[] { "acacia_leaves", "dark_oak_leaves" });
        META_VARIANTS.put("wooden_slab", new String[] { "oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab" });
        META_VARIANTS.put("stone", new String[] { "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite" });
        META_VARIANTS.put("dirt", new String[] { "dirt", "coarse_dirt", "podzol" });
        META_VARIANTS.put("sand", new String[] { "sand", "red_sand" });
        META_VARIANTS.put("sandstone", new String[] { "sandstone", "chiseled_sandstone", "cut_sandstone" });
        META_VARIANTS.put("red_sandstone", new String[] { "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone" });
        META_VARIANTS.put("stonebrick", new String[] { "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks" });
        META_VARIANTS.put("quartz_block", new String[] { "quartz_block", "chiseled_quartz_block", "quartz_pillar" });
        META_VARIANTS.put("prismarine", new String[] { "prismarine", "prismarine_bricks", "dark_prismarine" });
        META_VARIANTS.put("sponge", new String[] { "sponge", "wet_sponge" });
        META_VARIANTS.put("anvil", new String[] { "anvil", "chipped_anvil", "damaged_anvil" });
        META_VARIANTS.put("red_flower", new String[] { "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy" });
        META_VARIANTS.put("double_plant", new String[] { "sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony" });
        META_VARIANTS.put("tallgrass", new String[] { "dead_bush", "short_grass", "fern" });
        META_VARIANTS.put("fish", new String[] { "cod", "salmon", "tropical_fish", "pufferfish" });
        META_VARIANTS.put("cooked_fish", new String[] { "cooked_cod", "cooked_salmon" });
        META_VARIANTS.put("skull", new String[] { "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head" });
        META_VARIANTS.put("stone_slab", new String[] { "smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab" });
        META_VARIANTS.put("cobblestone_wall", new String[] { "cobblestone_wall", "mossy_cobblestone_wall" });
        META_VARIANTS.put("monster_egg", new String[] { "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks" });
    }

    private static void putColored(String legacy, String suffix) {
        String[] arr = new String[16];
        for (int i = 0; i < 16; i++) {
            arr[i] = COLORS[i] + suffix;
        }
        META_VARIANTS.put(legacy, arr);
    }

    private static void putColoredReversed(String legacy, String suffix) {
        String[] arr = new String[16];
        for (int i = 0; i < 16; i++) {
            arr[i] = COLORS[15 - i] + suffix;
        }
        META_VARIANTS.put(legacy, arr);
    }

    /**
     * Maps a 1.12 item name (optionally without namespace) and metadata value to its 1.21 item id.
     * Names that were not renamed by the flattening are returned unchanged (with namespace).
     */
    public static String legacyItemId(String name, int meta) {
        String s = name.trim().toLowerCase(Locale.ROOT);
        String ns = "minecraft";
        String path = s;
        int i = s.indexOf(':');
        if (i >= 0) {
            ns = s.substring(0, i);
            path = s.substring(i + 1);
        }
        if (!ns.equals("minecraft")) {
            return ns + ":" + path;
        }
        String[] variants = META_VARIANTS.get(path);
        if (variants != null) {
            if (meta >= 0 && meta < variants.length) {
                return "minecraft:" + variants[meta];
            }
            return "minecraft:" + variants[0];
        }
        String renamed = RENAMED.get(path);
        return "minecraft:" + (renamed != null ? renamed : path);
    }

    /** Whether an item with this id is registered; the default validator of converted recipes. */
    public static boolean isRegistered(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    private LegacyItemIds() {
    }
}
